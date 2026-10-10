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
)

for topic in "${TOPICS[@]}"; do
  for name in "$topic" "$topic.dlt"; do
    "$KAFKA_TOPICS" --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
      --topic "$name" --partitions 1 --replication-factor 1
  done
done

"$KAFKA_TOPICS" --bootstrap-server "$BOOTSTRAP" --list
