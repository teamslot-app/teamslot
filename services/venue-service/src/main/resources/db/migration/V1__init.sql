CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE venue (
  id UUID PRIMARY KEY,
  owner_id UUID NOT NULL,
  name TEXT NOT NULL,
  latitude DOUBLE PRECISION NOT NULL,
  longitude DOUBLE PRECISION NOT NULL,
  price_per_hour_cents BIGINT NOT NULL CHECK (price_per_hour_cents >= 0),
  currency CHAR(3) NOT NULL,
  accepts_on_site_payment BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE equipment (
  id UUID PRIMARY KEY,
  venue_id UUID NOT NULL REFERENCES venue(id),
  name TEXT NOT NULL,
  price_cents BIGINT NOT NULL CHECK (price_cents >= 0)
);

CREATE TABLE slot (
  id UUID PRIMARY KEY,
  venue_id UUID NOT NULL REFERENCES venue(id),
  start_at TIMESTAMPTZ NOT NULL,
  end_at TIMESTAMPTZ NOT NULL,
  CHECK (end_at > start_at),
  UNIQUE (venue_id, start_at),
  EXCLUDE USING gist (venue_id WITH =, tstzrange(start_at, end_at) WITH &&)
);

CREATE TABLE slot_reservation (
  id UUID PRIMARY KEY,
  slot_id UUID NOT NULL REFERENCES slot(id),
  match_id UUID NOT NULL,
  status TEXT NOT NULL DEFAULT 'ACTIVE',
  total_amount_cents BIGINT NOT NULL,
  currency CHAR(3) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_slot_reservation_active
  ON slot_reservation(slot_id) WHERE status = 'ACTIVE';

CREATE TABLE slot_reservation_equipment (
  reservation_id UUID NOT NULL REFERENCES slot_reservation(id),
  equipment_id UUID NOT NULL REFERENCES equipment(id),
  PRIMARY KEY (reservation_id, equipment_id)
);
