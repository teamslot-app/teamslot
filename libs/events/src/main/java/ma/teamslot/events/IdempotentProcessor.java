package ma.teamslot.events;

import java.util.UUID;

/**
 * Consommateur idempotent : un événement reçu deux fois n'a qu'un seul effet.
 * L'eventId est enregistré dans processed_event DANS LA MÊME transaction que le traitement :
 * si le traitement échoue, l'enregistrement est annulé et l'événement pourra être retraité.
 * Deux implémentations, choisies par l'auto-configuration selon la base du service (SCRUM-111) :
 * PostgreSQL (table processed_event) ou MongoDB (collection processed_event).
 */
public interface IdempotentProcessor {

    /** @return true si le traitement a été exécuté, false si l'événement avait déjà été traité. */
    boolean traiterUneFois(UUID eventId, Runnable traitement);
}
