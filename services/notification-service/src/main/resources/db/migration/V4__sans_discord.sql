-- SCRUM-81 : décision d'équipe du 14/11/2026, le joueur est prévenu dans l'application (liste « Mes notifications »)
-- et non plus dans un salon Discord. La file d'envoi Discord de V3 ne sert plus.
-- (On ne modifie jamais une migration déjà appliquée : V3 reste telle quelle, on supprime ici.)
DROP TABLE IF EXISTS discord_message;
