-- SCRUM-133 : code de vérification de l'e-mail. Un seul code actif par compte ; seule son empreinte BCrypt est
-- gardée (le code en clair ne circule que dans l'événement identity.email_verification_requested).
CREATE TABLE email_verification (
    user_id    BINARY(16)   NOT NULL PRIMARY KEY,
    code_hash  VARCHAR(100) NOT NULL,
    expires_at DATETIME(6)  NOT NULL,  -- UTC
    attempts   INT          NOT NULL DEFAULT 0,
    created_at DATETIME(6)  NOT NULL,  -- UTC
    CONSTRAINT fk_email_verification_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
