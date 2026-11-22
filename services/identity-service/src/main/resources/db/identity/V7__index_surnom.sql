-- SCRUM-82 : recherche de joueurs par début de surnom (LIKE 'texte%').
-- Sans index, chaque recherche lirait toute la table app_user.
CREATE INDEX idx_app_user_display_name ON app_user (display_name);
