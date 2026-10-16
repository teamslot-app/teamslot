#!/bin/sh
# Test de fumée (SCRUM-99), lancé par Argo CD après chaque synchronisation (hook PostSync).
# Aucun jeton, aucun secret : le test appelle seulement des routes publiques (santé)
# et vérifie que des routes protégées REFUSENT un appel sans jeton (401).
set -u
echc=0

ok() { echo "OK  $1"; }
ko() { echo "KO  $1"; echc=1; }

echo "== Versions déployées (tags d'image écrits par la CI dans overlays/staging) =="
for v in IMAGE_GATEWAY IMAGE_IDENTITY IMAGE_VENUE IMAGE_MATCH IMAGE_PAYMENT IMAGE_TEMPLATE; do
  eval "img=\${$v:-non renseignée}"
  echo "    $v=$img"
done

echo "== Santé (/actuator/health = UP, jusqu'à ~5 min par service) =="
for svc in gateway identity-service venue-service match-service payment-service service-template; do
  sante=0
  i=1
  while [ "$i" -le 30 ]; do
    if curl -fsS --max-time 5 "http://$svc/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
      sante=1
      break
    fi
    i=$((i + 1))
    sleep 10
  done
  if [ "$sante" = 1 ]; then ok "$svc : santé UP"; else ko "$svc : santé pas UP après ~5 min"; fi
done

echo "== Routes protégées appelées SANS jeton : 401 attendu (vérification du JWT active) =="
verifier_401() {
  code=$(curl -s -o /dev/null --max-time 10 -w '%{http_code}' "$1")
  if [ "$code" = 401 ]; then ok "$2 sans jeton -> 401"; else ko "$2 sans jeton : 401 attendu, reçu $code"; fi
}
verifier_401 "http://gateway/api/v1/matches"           "passerelle /api/v1/matches"
verifier_401 "http://venue-service/api/v1/venues"      "venue-service /api/v1/venues (SCRUM-87)"
verifier_401 "http://match-service/api/v1/matches"     "match-service /api/v1/matches"
verifier_401 "http://payment-service/api/v1/payments"  "payment-service /api/v1/payments"

if [ "$echc" = 0 ]; then
  echo "== RÉSULTAT : test de fumée réussi =="
else
  echo "== RÉSULTAT : ÉCHEC du test de fumée (voir les lignes KO ci-dessus) =="
fi
exit "$echc"
