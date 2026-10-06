package ma.teamslot.payment.events;

import java.util.UUID;

/**
 * Publication fiable des événements (docs/events.md, "Publication fiable").
 * A appeler DANS la même transaction que le changement d'état métier.
 */
public interface Outbox {

    /**
     * Enregistre un événement dans la table outbox_event. Le relais l'enverra ensuite à Kafka.
     *
     * @param aggregateId identifiant de l'agrégat (matchId, venueId...), clé du message Kafka
     * @param type        nom du topic du catalogue (ex. match.created)
     * @param data        contenu propre à l'événement (champ data de l'enveloppe)
     */
    void record(UUID aggregateId, String type, Object data);
}