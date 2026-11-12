-- SCRUM-136 : à l'inscription, l'utilisateur peut demander à être gérant de terrain.
-- La demande reste PENDING : seul un administrateur donne le rôle VENUE_MANAGER (SCRUM-137).
ALTER TABLE app_user
    ADD COLUMN manager_status       VARCHAR(10) NOT NULL DEFAULT 'NONE',
    ADD COLUMN manager_requested_at DATETIME(6) NULL, -- en UTC
    ADD CONSTRAINT ck_app_user_manager_status CHECK (manager_status IN ('NONE','PENDING','APPROVED','REJECTED'));

-- La liste des demandes en attente (SCRUM-137) filtre sur ce statut
CREATE INDEX idx_app_user_manager_status ON app_user (manager_status);
