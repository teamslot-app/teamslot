CREATE TABLE matches (
  id UUID PRIMARY KEY,
  kind TEXT NOT NULL,
  status TEXT NOT NULL,
  visibility TEXT NOT NULL,
  owner_id UUID NOT NULL,
  payer_id UUID NOT NULL,
  venue_id UUID NOT NULL,
  slot_id UUID NOT NULL,
  starts_at TIMESTAMPTZ NOT NULL,
  total_amount_cents BIGINT NOT NULL CHECK (total_amount_cents >= 0),
  currency CHAR(3) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_matches_owner ON matches(owner_id);
