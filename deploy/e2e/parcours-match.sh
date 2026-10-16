#!/bin/sh
# Test E2E du parcours « groupe d'amis » en staging (SCRUM-112, ADR-017).
# Passe par la passerelle, comme le front : inscription, connexion, créneau, match, amis, paiement, répartition.
# Tourne DANS le cluster (aucun port à ouvrir) :
#   kubectl run e2e-match --rm -i --restart=Never -n staging --image=curlimages/curl:8.10.1 \
#     --command -- sh -s < deploy/e2e/parcours-match.sh
# Crée à chaque passage deux comptes de test jetables (numéros aléatoires) et un match sur un terrain de démo.
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
mdp="E2e-test-$suffixe-staging"

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
if [ "$cotes" = 3 ]; then reussi "3 joueurs répartis (organisateur, ami, Yassir)"; else echoue "3 côtés attendus, $cotes trouvés"; fi

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
