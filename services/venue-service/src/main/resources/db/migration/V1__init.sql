-- venue-service : terrains, équipements et créneaux (contrat docs/api/venue.yaml)
-- Montants en centimes avec la devise ; heures en UTC.
-- La table des réservations (un créneau réservé une seule fois) viendra avec SCRUM-59.

CREATE TABLE venue (
    id                       UUID                     PRIMARY KEY,
    owner_id                 UUID                     NOT NULL,
    name                     VARCHAR(120)             NOT NULL,
    latitude                 DOUBLE PRECISION         NOT NULL,
    longitude                DOUBLE PRECISION         NOT NULL,
    price_per_hour_cents     BIGINT                   NOT NULL CHECK (price_per_hour_cents >= 0),
    currency                 CHAR(3)                  NOT NULL,
    accepts_on_site_payment  BOOLEAN                  NOT NULL DEFAULT FALSE,
    free_cancellation_hours  INTEGER,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE equipment (
    id                  UUID         PRIMARY KEY,
    venue_id            UUID         NOT NULL REFERENCES venue (id),
    name                VARCHAR(60)  NOT NULL,
    extra_price_cents   BIGINT       NOT NULL CHECK (extra_price_cents >= 0),
    currency            CHAR(3)      NOT NULL
);

-- Créneaux d'1 h ; un terrain n'a jamais deux créneaux qui commencent à la même heure.
CREATE TABLE slot (
    id        UUID                     PRIMARY KEY,
    venue_id  UUID                     NOT NULL REFERENCES venue (id),
    start_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    end_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_slot_venue_start UNIQUE (venue_id, start_at),
    CONSTRAINT ck_slot_one_hour CHECK (end_at > start_at)
);

CREATE INDEX idx_equipment_venue ON equipment (venue_id);
CREATE INDEX idx_slot_venue_start ON slot (venue_id, start_at);
