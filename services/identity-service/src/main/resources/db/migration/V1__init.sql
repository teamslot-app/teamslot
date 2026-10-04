-- SCRUM-57 : comptes et jetons de renouvellement d'identity-service
CREATE TABLE app_user (
    id              UUID                     PRIMARY KEY,
    phone           VARCHAR(20)              NOT NULL UNIQUE,
    phone_verified  BOOLEAN                  NOT NULL DEFAULT FALSE,
    password_hash   VARCHAR(100)             NOT NULL,
    display_name    VARCHAR(40)              NOT NULL,
    platform_role   VARCHAR(20)              NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

-- On ne stocke jamais le jeton de renouvellement lui-même, seulement son empreinte SHA-256.
CREATE TABLE refresh_token (
    id          UUID                     PRIMARY KEY,
    user_id     UUID                     NOT NULL REFERENCES app_user (id),
    token_hash  VARCHAR(64)              NOT NULL UNIQUE,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked     BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_refresh_token_user ON refresh_token (user_id);
