#!/bin/sh
# Test E2E du parcours « groupe d'amis » en staging (SCRUM-112, ADR-017).
# Passe par la passerelle, comme le front : inscription, connexion, créneau, match, amis, places ouvertes,
# demande et acceptation d'un joueur extérieur, paiement, répartition, notifications dans l'application, annulation.
# Tourne DANS le cluster (aucun port à ouvrir) :
#   kubectl run e2e-match --rm -i --restart=Never -n staging --image=curlimages/curl:8.10.1 \
#     --command -- sh -s < deploy/e2e/parcours-match.sh
# Crée à chaque passage trois comptes de test jetables (numéros aléatoires) et un match sur un terrain de démo.
# N'affiche ni mot de passe ni jeton.
set -u

API="${API:-http://gateway}"
TERRAIN="${TERRAIN:-a1000000-0000-4000-8000-000000000001}" # Terrain Démo Guéliz (profil demo de venue-service)
ok=0
ko=0
REPONSE=$(mktemp)
trap 'rm -f "$REPONSE"' EXIT

reussi() { echo "OK  $1"; ok=$((ok + 1)); }
echoue() { echo "KO  $1"; ko=$((ko + 1)); }
fin() {
  echo "== RÉSULTAT : $ok OK / $ko KO =="
  [ "$ko" = 0 ]
  exit $?
}

# Valeur texte d'un champ JSON simple : champ "<json>" id
champ() { printf '%s' "$1" | sed -n "s/.*\"$2\":\"\([^\"]*\)\".*/\1/p" | head -n 1; }

# appel METHODE CHEMIN [JETON] [CORPS] -> CODE dans $code, réponse dans $corps
appel() {
  methode=$1
  chemin=$2
  jeton=${3:-}
  donnees=${4:-}
  set -- -s -o "$REPONSE" -w '%{http_code}' --max-time 30 -X "$methode" -H 'Content-Type: application/json'
  [ -n "$jeton" ] && set -- "$@" -H "Authorization: Bearer $jeton"
  [ -n "$donnees" ] && set -- "$@" -d "$donnees"
  # 000 = pas de réponse (connexion coupée, délai dépassé) : jusqu'à 3 essais, jamais sur une vraie réponse HTTP
  essai=1
  while :; do
    : > "$REPONSE"
    code=$(curl "$@" "$API$chemin")
    [ "$code" != 000 ] || [ "$essai" -ge 3 ] && break
    essai=$((essai + 1))
    sleep 3
  done
  corps=$(cat "$REPONSE" 2>/dev/null)
}

attendu() { # attendu CODE_ATTENDU LIBELLÉ
  if [ "$code" = "$1" ]; then reussi "$2 ($code)"; else echoue "$2 : $1 attendu, reçu $code $(printf '%s' "$corps" | head -c 200)"; fi
}

suffixe=$(date +%s | tail -c 9)
# SCRUM-132 : le mot de passe ne doit pas contenir les chiffres du numéro -> lettres aléatoires, jamais affichées
mdp="E2e-$(head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z' | head -c 12)-Staging"

echo "== 1. Comptes de test =="
appel POST /api/v1/auth/register "" "{\"phone\":\"+2126$suffixe\",\"password\":\"$mdp\",\"displayName\":\"E2E Orga $suffixe\"}"
attendu 201 "inscription de l'organisateur"
appel POST /api/v1/auth/login "" "{\"phone\":\"+2126$suffixe\",\"password\":\"$mdp\"}"
attendu 200 "connexion de l'organisateur"
orga=$(champ "$corps" accessToken)

appel POST /api/v1/auth/register "" "{\"phone\":\"+2127$suffixe\",\"password\":\"$mdp\",\"displayName\":\"E2E Ami $suffixe\"}"
attendu 201 "inscription de l'ami"
ami_id=$(champ "$corps" id)
appel POST /api/v1/auth/login "" "{\"phone\":\"+2127$suffixe\",\"password\":\"$mdp\"}"
attendu 200 "connexion de l'ami"
ami=$(champ "$corps" accessToken)
[ -n "$orga" ] && [ -n "$ami" ] && [ -n "$ami_id" ] || { echoue "jetons ou id de l'ami absents : arrêt"; fin; }

echo "== 2. Créneau libre (terrain de démo, jours J+1 à J+7) =="
creneau=""
j=1
while [ "$j" -le 7 ] && [ -z "$creneau" ]; do
  jour=$(date -u -d "@$(( $(date +%s) + 86400 * j ))" +%Y-%m-%d)
  appel GET "/api/v1/venues/$TERRAIN/slots?date=$jour" "$orga"
  if [ "$code" = 200 ]; then
    creneau=$(printf '%s' "$corps" | tr '{' '\n' | grep '"available":true' | head -n 1 | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')
  fi
  j=$((j + 1))
done
if [ -n "$creneau" ]; then reussi "créneau libre trouvé le $jour"; else echoue "aucun créneau libre sur 7 jours : arrêt"; fin; fi

echo "== 3. Match (match-service sur MongoDB) =="
appel POST /api/v1/matches "$orga" "{\"slotId\":\"$creneau\"}"
attendu 201 "création du match"
match=$(champ "$corps" id)
case "$corps" in *'"status":"PENDING_PAYMENT"'*) reussi "match en attente de paiement" ;; *) echoue "statut PENDING_PAYMENT attendu" ;; esac
[ -n "$match" ] || { echoue "id du match absent : arrêt"; fin; }

echo "== 4. Participants =="
appel POST "/api/v1/matches/$match/participants" "$orga" "{\"userId\":\"$ami_id\"}"
attendu 201 "ajout de l'ami (compte)"
appel POST "/api/v1/matches/$match/participants" "$orga" '{"guestName":"Yassir"}'
attendu 201 "ajout d'un invité sans compte"
appel POST "/api/v1/matches/$match/participants" "$orga" '{"guestName":" yas sir "}'
attendu 409 "même surnom (casse et espaces ignorés) refusé"
appel POST "/api/v1/matches/$match/participants" "$ami" '{"guestName":"Karim"}'
attendu 403 "un non-organisateur ne peut pas ajouter"
appel POST "/api/v1/matches/$match/participants" "" '{"guestName":"Karim"}'
attendu 401 "sans jeton"

echo "== 4 bis. Ouvrir des places (SCRUM-78) =="
# 3 joueurs inscrits (organisateur, ami, Yassir) et 10 au plus : 7 places au maximum.
appel POST "/api/v1/matches/$match/open-spots" "$ami" '{"openSpots":2}'
attendu 403 "un non-organisateur ne peut pas ouvrir de places"
appel POST "/api/v1/matches/$match/open-spots" "$orga" '{"openSpots":0}'
attendu 400 "zéro place refusé"
appel POST "/api/v1/matches/$match/open-spots" "$orga" '{"openSpots":8}'
attendu 400 "plus de places que la capacité refusé"
appel POST "/api/v1/matches/$match/open-spots" "$orga" '{"openSpots":2}'
attendu 200 "l'organisateur ouvre 2 places"
case "$corps" in *'"openSpots":2'*) reussi "2 places ouvertes" ;; *) echoue "openSpots 2 attendu" ;; esac
appel POST "/api/v1/matches/$match/open-spots" "" '{"openSpots":2}'
attendu 401 "ouvrir des places sans jeton"

echo "== 4 ter. Matchs ouverts (SCRUM-118) =="
# Le jour du créneau ($jour, UTC) est aussi le jour à Marrakech : UTC+0 depuis le 20/09/2026 (tzdata 2026c).
appel GET "/api/v1/matches/open?date=$jour" "$ami"
attendu 200 "l'ami voit les matchs ouverts du $jour"
case "$corps" in *"\"id\":\"$match\""*) reussi "le match aux places ouvertes est listé" ;; *) echoue "match $match absent de la liste" ;; esac
case "$corps" in *Yassir*|*guestName*|*ownerId*) echoue "la liste contient des données de participants" ;; *) reussi "aucune donnée de participant dans la liste" ;; esac
appel GET "/api/v1/matches/open?date=$jour" ""
attendu 401 "matchs ouverts sans jeton"

echo "== 4 quater. Rejoindre un match (SCRUM-79) =="
# Un troisième compte, qui n'est pas encore dans le match, demande une des 2 places ouvertes.
appel POST /api/v1/auth/register "" "{\"phone\":\"+2125$suffixe\",\"password\":\"$mdp\",\"displayName\":\"E2E Joueur $suffixe\"}"
attendu 201 "inscription du joueur extérieur"
joueur_id=$(champ "$corps" id)
appel POST /api/v1/auth/login "" "{\"phone\":\"+2125$suffixe\",\"password\":\"$mdp\"}"
attendu 200 "connexion du joueur extérieur"
joueur=$(champ "$corps" accessToken)
[ -n "$joueur" ] && [ -n "$joueur_id" ] || { echoue "jeton ou id du joueur extérieur absent : arrêt"; fin; }
appel POST "/api/v1/matches/$match/join" "$joueur"
attendu 202 "le joueur demande à rejoindre"
appel POST "/api/v1/matches/$match/join" "$joueur"
attendu 409 "deuxième demande du même joueur refusée"
appel POST "/api/v1/matches/$match/join" "$ami"
attendu 409 "un participant ne peut pas demander"
appel GET "/api/v1/matches/$match/join-requests" "$ami"
attendu 403 "un non-organisateur ne voit pas les demandes"
appel GET "/api/v1/matches/$match/join-requests" "$orga"
attendu 200 "l'organisateur voit les demandes"
case "$corps" in *"$joueur_id"*) reussi "la demande du joueur est listée" ;; *) echoue "demande du joueur absente" ;; esac
appel POST "/api/v1/matches/$match/join-requests/$joueur_id/accept" "$ami"
attendu 403 "un non-organisateur ne peut pas accepter"
appel POST "/api/v1/matches/$match/join-requests/$joueur_id/accept" "$orga"
attendu 200 "l'organisateur accepte le joueur"
case "$corps" in *'"status":"CONFIRMED"'*) reussi "joueur CONFIRMED" ;; *) echoue "statut CONFIRMED attendu" ;; esac
appel POST "/api/v1/matches/$match/join-requests/$joueur_id/accept" "$orga"
attendu 404 "accepter deux fois : plus de demande"
appel POST "/api/v1/matches/$match/join" ""
attendu 401 "demander sans jeton"

echo "== 5. Paiement en ligne (payment-service, puis événement Kafka vers match-service) =="
i=0
code=000
while [ "$i" -lt 30 ] && [ "$code" != 200 ]; do
  appel GET "/api/v1/payments/$match" "$orga"
  [ "$code" = 200 ] || sleep 2
  i=$((i + 1))
done
attendu 200 "paiement créé par match.created (Kafka)"
appel POST "/api/v1/payments/$match/online" "$orga"
attendu 200 "paiement en ligne"
case "$corps" in *'"status":"COMPLETED"'*) reussi "paiement COMPLETED" ;; *) echoue "statut COMPLETED attendu" ;; esac

echo "== 6. Répartition (match CONFIRMED par payment.completed) =="
i=0
code=000
while [ "$i" -lt 30 ] && [ "$code" != 200 ]; do
  appel POST "/api/v1/matches/$match/split?strategy=random" "$orga"
  [ "$code" = 200 ] || sleep 2
  i=$((i + 1))
done
attendu 200 "répartition après confirmation du match"
appel POST "/api/v1/matches/$match/split?strategy=random" "$ami"
attendu 403 "un non-organisateur ne peut pas répartir"

echo "== 7. Liste des participants vue par l'ami =="
appel GET "/api/v1/matches/$match/participants" "$ami"
attendu 200 "l'ami voit la liste"
cotes=$(printf '%s' "$corps" | grep -o '"side":"[AB]"' | wc -l | tr -d ' ')
if [ "$cotes" = 4 ]; then reussi "4 joueurs répartis (organisateur, ami, Yassir, joueur accepté)"; else echoue "4 côtés attendus, $cotes trouvés"; fi

echo "== 7 bis. Notifications dans l'application (SCRUM-81) =="
# notification-service consomme les événements Kafka : on attend jusqu'à 60 s que les notifications arrivent.
# L'ami a été ajouté (participant_added) puis le match est confirmé (confirmed) ; le joueur extérieur a été accepté.
i=0
vu=non
while [ "$i" -lt 30 ] && [ "$vu" = non ]; do
  appel GET /api/v1/notifications "$ami"
  case "$corps" in *MATCH_PARTICIPANT_ADDED*MATCH_CONFIRMED*|*MATCH_CONFIRMED*MATCH_PARTICIPANT_ADDED*) vu=oui ;; *) sleep 2 ;; esac
  i=$((i + 1))
done
attendu 200 "l'ami lit ses notifications"
if [ "$vu" = oui ]; then reussi "l'ami est prévenu : ajouté au match, puis match confirmé"; else echoue "notifications de l'ami absentes après 60 s"; fi
case "$corps" in *"\"matchId\":\"$match\""*) reussi "les notifications portent le bon match" ;; *) echoue "match $match absent des notifications" ;; esac
case "$corps" in *Yassir*|*phone*|*displayName*) echoue "une notification contient une donnée personnelle" ;; *) reussi "aucune donnée personnelle dans les notifications" ;; esac
notif=$(champ "$corps" id)
[ -n "$notif" ] || { echoue "id de notification absent : arrêt"; fin; }
appel POST "/api/v1/notifications/$notif/read" "$orga"
attendu 404 "l'organisateur ne peut pas marquer la notification de l'ami"
appel POST "/api/v1/notifications/$notif/read" "$ami"
attendu 204 "l'ami marque sa notification comme lue"
appel POST "/api/v1/notifications/$notif/read" "$ami"
attendu 204 "marquer deux fois : même résultat"
appel GET /api/v1/notifications "$ami"
case "$corps" in *'"readAt":"'*) reussi "la notification est lue" ;; *) echoue "readAt attendu après lecture" ;; esac
i=0
vu=non
while [ "$i" -lt 30 ] && [ "$vu" = non ]; do
  appel GET /api/v1/notifications "$joueur"
  case "$corps" in *MATCH_JOIN_ACCEPTED*) vu=oui ;; *) sleep 2 ;; esac
  i=$((i + 1))
done
if [ "$vu" = oui ]; then reussi "le joueur accepté est prévenu"; else echoue "notification d'acceptation absente après 60 s"; fi
appel GET "/api/v1/notifications?limit=0" "$ami"
attendu 400 "limit hors bornes refusé"
appel GET /api/v1/notifications ""
attendu 401 "mes notifications sans jeton"

echo "== 8. Annulation d'un match payé (SCRUM-76) =="
# Les passages précédents occupent des créneaux : on cherche à nouveau sur J+1 à J+7.
creneau2=""
j=1
while [ "$j" -le 7 ] && [ -z "$creneau2" ]; do
  jour2=$(date -u -d "@$(( $(date +%s) + 86400 * j ))" +%Y-%m-%d)
  appel GET "/api/v1/venues/$TERRAIN/slots?date=$jour2" "$orga"
  if [ "$code" = 200 ]; then
    creneau2=$(printf '%s' "$corps" | tr '{' '\n' | grep '"available":true' | head -n 1 | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')
  fi
  j=$((j + 1))
done
if [ -n "$creneau2" ]; then reussi "second créneau libre trouvé le $jour2"; else echoue "aucun second créneau libre sur 7 jours : arrêt"; fin; fi
appel POST /api/v1/matches "$orga" "{\"slotId\":\"$creneau2\"}"
attendu 201 "second match"
match2=$(champ "$corps" id)
i=0
code=000
while [ "$i" -lt 30 ] && [ "$code" != 200 ]; do
  appel GET "/api/v1/payments/$match2" "$orga"
  [ "$code" = 200 ] || sleep 2
  i=$((i + 1))
done
appel POST "/api/v1/payments/$match2/online" "$orga"
attendu 200 "paiement en ligne du second match"
appel POST "/api/v1/matches/$match2/cancel" "$ami"
attendu 403 "un non-organisateur ne peut pas annuler"
appel POST "/api/v1/matches/$match2/cancel" "$orga"
attendu 204 "annulation par l'organisateur"
appel POST "/api/v1/matches/$match2/cancel" "$orga"
attendu 409 "un match déjà annulé ne peut pas l'être deux fois"
i=0
rembourse=non
while [ "$i" -lt 30 ] && [ "$rembourse" = non ]; do
  appel GET "/api/v1/payments/$match2" "$orga"
  case "$corps" in *'"status":"REFUNDED"'*) rembourse=oui ;; *) sleep 2 ;; esac
  i=$((i + 1))
done
if [ "$rembourse" = oui ]; then reussi "paiement remboursé (match.cancelled -> payment-service)"; else echoue "paiement pas REFUNDED après 60 s"; fi
i=0
libre=non
while [ "$i" -lt 30 ] && [ "$libre" = non ]; do
  appel GET "/api/v1/venues/$TERRAIN/slots?date=$jour2" "$orga"
  if printf '%s' "$corps" | tr '{' '\n' | grep "\"id\":\"$creneau2\"" | grep -q '"available":true'; then
    libre=oui
  else
    sleep 2
  fi
  i=$((i + 1))
done
if [ "$libre" = oui ]; then reussi "créneau de nouveau libre (match.cancelled -> venue-service)"; else echoue "créneau toujours réservé après 60 s"; fi

echo "    matchs testés : $match (joué), $match2 (annulé)"
fin
