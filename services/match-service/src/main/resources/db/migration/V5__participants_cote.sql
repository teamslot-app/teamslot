-- SCRUM-61 : côté (A ou B) de chaque participant après la répartition en équipes.
-- NULL tant que le match n'a pas été réparti.
ALTER TABLE participants ADD COLUMN side TEXT;
ALTER TABLE participants ADD CONSTRAINT participant_cote CHECK (side IN ('A', 'B'));
