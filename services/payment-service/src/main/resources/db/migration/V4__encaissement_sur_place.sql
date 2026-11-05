-- SCRUM-75 : le gérant du terrain confirme l'encaissement sur place.
-- venue_id : terrain du match (match.created). Nul pour les paiements créés avant ce ticket :
-- un paiement sans terrain connu ne peut pas être encaissé (refus par défaut, 403).
ALTER TABLE payments ADD COLUMN venue_id UUID;
ALTER TABLE payments ADD COLUMN collected_by UUID;
ALTER TABLE payments ADD COLUMN collected_at TIMESTAMPTZ;

-- Gérant de chaque terrain, appris de venue.created (ownerId = sub du gérant qui a créé le terrain).
-- Projection locale : payment ne demande rien à venue-service pendant la requête.
CREATE TABLE venue_owners (
    venue_id   UUID        PRIMARY KEY,
    owner_id   UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
