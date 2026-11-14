-- notification-service (SCRUM-128) : squelette, aucune table métier pour l'instant.
-- V2__outbox.sql (outbox_event, processed_event) vient de libs/events (ADR-015).
-- Les tables des notifications arrivent avec SCRUM-81 (V3__…) : ne jamais modifier V1 ou V2.
SELECT 1;
