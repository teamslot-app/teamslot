-- SCRUM-132 : inscription v2 (contrat identity 2.0.0). Colonnes facultatives pendant la migration :
-- les comptes déjà créés n'ont ni e-mail ni date de naissance, et apps/web ne les envoie pas encore.
ALTER TABLE app_user
    ADD COLUMN email          VARCHAR(254) NULL,
    ADD COLUMN email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN birth_date     DATE         NULL,
    ADD COLUMN phone_country  VARCHAR(2)   NULL;

-- Enregistré en minuscules par le service ; UNIQUE accepte plusieurs NULL (anciens comptes)
CREATE UNIQUE INDEX ux_app_user_email ON app_user (email);
