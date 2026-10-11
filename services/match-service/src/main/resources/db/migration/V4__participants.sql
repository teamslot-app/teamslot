-- SCRUM-60 : participants d'un match, soit un compte (user_id), soit un invite sans compte (guest_name).
-- Aucune colonne pour un numero ou un e-mail : rien de tel n'est stocke pour un invite.
CREATE TABLE participants (
    id         UUID        PRIMARY KEY,
    match_id   UUID        NOT NULL REFERENCES matches (id),
    user_id    UUID,
    guest_name TEXT,
    status     TEXT        NOT NULL,
    added_by   UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT participant_compte_ou_surnom CHECK ((user_id IS NULL) <> (guest_name IS NULL)),
    CONSTRAINT participant_surnom_longueur CHECK (guest_name IS NULL OR char_length(guest_name) BETWEEN 1 AND 40),
    CONSTRAINT participant_statut CHECK (status IN ('INVITED', 'CONFIRMED', 'REQUESTED', 'DECLINED'))
);

-- Un meme compte n'est ajoute qu'une fois a un match.
CREATE UNIQUE INDEX uq_participants_match_compte ON participants (match_id, user_id) WHERE user_id IS NOT NULL;
-- Decision PO (Laila) : un surnom n'est utilise qu'une fois par match, sans tenir compte de la casse
-- ni des espaces, car match.teams_split identifie les invites par guest:<surnom> (SCRUM-61).
CREATE UNIQUE INDEX uq_participants_match_surnom ON participants (match_id, lower(replace(guest_name, ' ', '')))
    WHERE guest_name IS NOT NULL;
CREATE INDEX idx_participants_match ON participants (match_id);
