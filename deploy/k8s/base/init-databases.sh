#!/bin/bash
set -e

create_db() {
  local db="$1" user="$2" pass="$3"
  psql -v ON_ERROR_STOP=1 -v pass="$pass" --username "$POSTGRES_USER" --dbname postgres <<-EOSQL
    CREATE USER ${user} WITH PASSWORD :'pass';
    CREATE DATABASE ${db} OWNER ${user};
    REVOKE ALL ON DATABASE ${db} FROM PUBLIC;
EOSQL
}

create_db template teamslot "$TEMPLATE_DB_PASSWORD"
create_db venue venue_user "$VENUE_DB_PASSWORD"
# match : plus de base PostgreSQL depuis SCRUM-112, match-service est sur MongoDB (ADR-017)
create_db payment payment_user "$PAYMENT_DB_PASSWORD"
# notification (SCRUM-128) : seulement si son mot de passe est fourni (jamais de compte sans mot de passe).
# Dans le staging déjà installé, la base a été créée à la main (docs/bases-de-donnees.md) : ce script ne tourne
# que sur un volume neuf (premier démarrage de PostgreSQL, docker-compose ou nouveau cluster).
if [ -n "${NOTIFICATION_DB_PASSWORD:-}" ]; then
  create_db notification notification_user "$NOTIFICATION_DB_PASSWORD"
fi
