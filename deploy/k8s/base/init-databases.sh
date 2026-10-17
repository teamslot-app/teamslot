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
