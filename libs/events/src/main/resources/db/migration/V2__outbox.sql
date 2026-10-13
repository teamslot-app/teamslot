-- SCRUM-70 : publication fiable par outbox et consommateur idempotent

-- Événements à publier, écrits dans la même transaction que le changement d'état
CREATE TABLE outbox_event (
    id           UUID        PRIMARY KEY,                          -- = eventId de l'enveloppe
    position     BIGINT      GENERATED ALWAYS AS IDENTITY UNIQUE,  -- ordre exact d'enregistrement
    aggregate_id UUID        NOT NULL,                             -- clé du message Kafka
    event_type   TEXT        NOT NULL,                             -- = nom du topic (ex. match.created)
    payload      JSONB       NOT NULL,                             -- enveloppe complète
    occurred_at  TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ                                       -- NULL tant que non publié
);

-- Le relais ne lit que les lignes non publiées, dans l'ordre
CREATE INDEX idx_outbox_event_a_publier
    ON outbox_event (position)
    WHERE published_at IS NULL;

-- Événements déjà traités par ce service (idempotence)
CREATE TABLE processed_event (
    event_id     UUID        PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);