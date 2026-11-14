#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVERS:-kafka:19092}"
KAFKA_TOPICS="/opt/kafka/bin/kafka-topics.sh"

# Topics du Sprint 1 (docs/events.md, colonne S1)
TOPICS=(
  user.registered
  slot.reserved
  slot.released
  match.created
  match.participant_added
  match.confirmed
  match.teams_split
  payment.completed
  payment.on_site_accepted
  # SCRUM-76 : annulation d'un match (venue libère le créneau, payment rembourse)
  match.cancelled
  payment.refunded
  # SCRUM-74 : un gérant publie un terrain
  venue.created
  # SCRUM-75 : le gérant confirme l'encaissement sur place
  payment.collected_on_site
  # SCRUM-78 : l'organisateur ouvre des places pour compléter son groupe
  match.spots_opened
  # SCRUM-79 : un joueur demande une place, l'organisateur accepte
  match.join_requested
  match.join_accepted
  # SCRUM-133 : code de vérification de l'e-mail (consommé par notification-service, SCRUM-134)
  identity.email_verification_requested
)

for topic in "${TOPICS[@]}"; do
  for name in "$topic" "$topic.dlt"; do
    "$KAFKA_TOPICS" --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
      --topic "$name" --partitions 1 --replication-factor 1
  done
done

"$KAFKA_TOPICS" --bootstrap-server "$BOOTSTRAP" --list
