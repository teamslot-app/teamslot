package ma.teamslot.match.events;

import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lit un message Kafka (texte JSON) en enveloppe. Un message reçu est une entrée NON FIABLE :
 * les vérifications du constructeur de EventEnvelope s'appliquent, un message invalide lève une exception.
 */
@Component
public class EventReader {

    private static final TypeReference<EventEnvelope<JsonNode>> TYPE_ENVELOPPE = new TypeReference<>() {
    };

    private final JsonMapper jsonMapper;

    public EventReader(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public EventEnvelope<JsonNode> lire(String message) {
        return jsonMapper.readValue(message, TYPE_ENVELOPPE);
    }
}
