-- SCRUM-62 : un paiement par match, cree a la reception de match.created (statut PENDING).
-- Le montant vient de l'evenement (calcule par venue puis match), jamais du client.
CREATE TABLE payments (
    id                      UUID        PRIMARY KEY,
    match_id                UUID        NOT NULL UNIQUE,          -- S6 : un seul paiement par match
    payer_id                UUID        NOT NULL,
    total_amount_cents      BIGINT      NOT NULL CHECK (total_amount_cents >= 0),
    currency                CHAR(3)     NOT NULL,
    accepts_on_site_payment BOOLEAN     NOT NULL,
    mode                    TEXT        CHECK (mode IN ('ONLINE', 'ON_SITE')),
    status                  TEXT        NOT NULL
        CHECK (status IN ('PENDING', 'COMPLETED', 'ON_SITE_ACCEPTED', 'COLLECTED', 'FAILED', 'REFUNDED')),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
