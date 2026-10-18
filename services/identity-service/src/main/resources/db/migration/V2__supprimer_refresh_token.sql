-- SCRUM-110 : les refresh tokens sont désormais dans Redis (TTL), la table n'est plus utilisée
DROP TABLE refresh_token;
