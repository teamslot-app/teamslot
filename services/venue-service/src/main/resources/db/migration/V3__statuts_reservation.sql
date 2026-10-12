-- Vocabulaire du contrat venue.yaml : RESERVED / RELEASED (la base disait ACTIVE).
DROP INDEX uq_slot_reservation_active;
UPDATE slot_reservation SET status = 'RESERVED' WHERE status = 'ACTIVE';
ALTER TABLE slot_reservation ALTER COLUMN status SET DEFAULT 'RESERVED';
ALTER TABLE slot_reservation
  ADD CONSTRAINT ck_slot_reservation_status CHECK (status IN ('RESERVED', 'RELEASED'));
CREATE UNIQUE INDEX uq_slot_reservation_reserved ON slot_reservation(slot_id) WHERE status = 'RESERVED';
