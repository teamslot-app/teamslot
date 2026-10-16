package ma.teamslot.payment.adapter.in.kafka;

import java.util.Optional;
import java.util.UUID;
import ma.teamslot.events.EventEnvelope;
import ma.teamslot.events.EventReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lecture commune des événements consommés par payment-service : enveloppe, filtre sur le type et la version,
 * puis data typée. Une autre version publiée en parallèle pendant une migration est ignorée (on ne lit que la v1).
 * Un message illisible ou une data invalide lève une exception : le message part au .dlt.
 */
@Component
class LecteurDEvenements {

    private static final int VERSION_LUE = 1;
    private static final Logger LOG = LoggerFactory.getLogger(LecteurDEvenements.class);

    private final EventReader lecteur;
    private final JsonMapper json;

    LecteurDEvenements(EventReader lecteur, JsonMapper json) {
        this.lecteur = lecteur;
        this.json = json;
    }

    <T> Optional<Recu<T>> lire(String message, String topic, Class<T> typeData) {
        EventEnvelope<JsonNode> evenement = lecteur.lire(message);
        if (!topic.equals(evenement.type()) || evenement.version() != VERSION_LUE) {
            LOG.info("Evenement ignore {} ({} v{})", evenement.eventId(), evenement.type(), evenement.version());
            return Optional.empty();
        }
        return Optional.of(new Recu<>(evenement.eventId(), json.treeToValue(evenement.data(), typeData)));
    }

    /** Événement accepté : son identifiant (pour l'idempotence) et sa data. */
    record Recu<T>(UUID eventId, T data) {
    }
}
