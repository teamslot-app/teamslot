-- SCRUM-81 : notifications des joueurs (contrat docs/api/notification.yaml, SCRUM-71).

-- Projection locale « match -> joueurs avec compte », remplie par match.participant_added et
-- match.join_accepted : match.confirmed ne contient que matchId. Aucun invité sans compte ici.
CREATE TABLE match_participant (
    match_id UUID NOT NULL,
    user_id  UUID NOT NULL,
    PRIMARY KEY (match_id, user_id)
);

-- Notification personnelle (liste « Mes notifications »). Ni texte libre ni donnée personnelle :
-- le front construit le message à partir de type et match_id.
CREATE TABLE notification (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL,
    type       VARCHAR(40) NOT NULL
               CHECK (type IN ('MATCH_PARTICIPANT_ADDED', 'MATCH_JOIN_ACCEPTED', 'MATCH_CONFIRMED')),
    match_id   UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at    TIMESTAMPTZ
);
CREATE INDEX idx_notification_user_recentes ON notification (user_id, created_at DESC, id DESC);

-- Messages à publier dans le salon Discord de l'équipe, envoyés hors de la transaction Kafka
-- (Discord en panne = message en attente, réessayé, jamais perdu ni envoyé deux fois).
CREATE TABLE discord_message (
    id             UUID          PRIMARY KEY,
    contenu        VARCHAR(2000) NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    sent_at        TIMESTAMPTZ,
    essais         INT           NOT NULL DEFAULT 0,
    prochain_essai TIMESTAMPTZ   NOT NULL DEFAULT now(),
    abandonne      BOOLEAN       NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_discord_message_a_envoyer ON discord_message (prochain_essai)
    WHERE sent_at IS NULL AND NOT abandonne;
