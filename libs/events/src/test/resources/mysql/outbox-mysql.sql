-- Copie de services/identity-service/src/main/resources/db/identity/V5__outbox.sql (SCRUM-147) :
-- tables MySQL de l'outbox, pour tester le SQL MySQL de libs/events. A garder identique.

-- Événements à publier, écrits dans la même transaction que le changement d'état
CREATE TABLE outbox_event (
    id           CHAR(36)     NOT NULL PRIMARY KEY,          -- = eventId de l'enveloppe
    position     BIGINT       NOT NULL AUTO_INCREMENT UNIQUE, -- ordre exact d'enregistrement
    aggregate_id CHAR(36)     NOT NULL,                      -- clé du message Kafka
    event_type   VARCHAR(100) NOT NULL,                      -- = nom du topic (ex. user.registered)
    payload      JSON         NOT NULL,                      -- enveloppe complète
    occurred_at  DATETIME(6)  NOT NULL,                      -- UTC
    published_at DATETIME(6)  NULL                           -- UTC ; NULL tant que non publié
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Le relais lit les lignes non publiées, dans l'ordre (MySQL n'a pas d'index partiel)
CREATE INDEX idx_outbox_event_a_publier ON outbox_event (published_at, position);

-- Événements déjà traités par ce service (idempotence)
CREATE TABLE processed_event (
    event_id     CHAR(36)    NOT NULL PRIMARY KEY,
    processed_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
