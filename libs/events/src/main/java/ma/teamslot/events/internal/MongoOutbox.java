package ma.teamslot.events.internal;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import ma.teamslot.events.EventEnvelope;
import ma.teamslot.events.Outbox;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Outbox pour les services MongoDB (SCRUM-111) : écrit l'événement dans la collection outbox_event,
 * dans la transaction MongoDB de l'appelant. Même enveloppe que JdbcOutbox.
 * Classe interne : les services utilisent l'interface Outbox.
 */
class MongoOutbox implements Outbox {

    static final String COLLECTION = "outbox_event";

    private final MongoTemplate mongo;
    private final MongoDatabaseFactory fabrique;
    private final JsonMapper jsonMapper;
    private final String producer;

    MongoOutbox(MongoTemplate mongo, MongoDatabaseFactory fabrique, JsonMapper jsonMapper, String producer) {
        this.mongo = mongo;
        this.fabrique = fabrique;
        this.jsonMapper = jsonMapper;
        this.producer = producer;
    }

    /** Refuse d'écrire hors transaction MongoDB, comme JdbcOutbox (propagation MANDATORY). */
    @Override
    public void record(UUID aggregateId, String type, Object data) {
        Objects.requireNonNull(aggregateId, "aggregateId est obligatoire");
        // MongoTransactionManager lie la session de la transaction à la fabrique : sans elle, l'écriture
        // partirait seule et l'événement pourrait exister sans le changement d'état (ou l'inverse).
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(fabrique)) {
            throw new IllegalTransactionStateException(
                    "outbox.record doit être appelé dans une transaction MongoDB (@Transactional)");
        }
        EventEnvelope<Object> envelope = EventEnvelope.of(type, producer, data);

        Document ligne = new Document("_id", envelope.eventId().toString())
                // ObjectId créé ici : croissant dans ce processus (un seul pod), donne l'ordre d'enregistrement
                // sans compteur partagé (un compteur provoquerait des conflits d'écriture entre transactions).
                .append("position", new ObjectId())
                .append("aggregateId", aggregateId.toString())
                .append("eventType", envelope.type())
                .append("payload", jsonMapper.writeValueAsString(envelope))
                .append("occurredAt", Date.from(envelope.occurredAt()))
                .append("publishedAt", null);
        mongo.insert(ligne, COLLECTION);
    }
}
