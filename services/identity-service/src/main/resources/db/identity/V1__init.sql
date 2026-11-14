-- SCRUM-57 / SCRUM-109 : comptes et jetons de renouvellement d'identity-service (MySQL 8)
CREATE TABLE app_user (
    id              BINARY(16)   NOT NULL PRIMARY KEY,
    phone           VARCHAR(20)  NOT NULL UNIQUE,
    phone_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    password_hash   VARCHAR(100) NOT NULL,
    display_name    VARCHAR(40)  NOT NULL,
    platform_role   VARCHAR(20)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL, -- toujours en UTC
    CONSTRAINT ck_app_user_role CHECK (platform_role IN ('PLAYER','VENUE_MANAGER','ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- On ne stocke jamais le jeton de renouvellement lui-même, seulement son empreinte SHA-256.
CREATE TABLE refresh_token (
    id          BINARY(16)  NOT NULL PRIMARY KEY,
    user_id     BINARY(16)  NOT NULL,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  DATETIME(6) NOT NULL,
    revoked     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_refresh_token_user ON refresh_token (user_id);
