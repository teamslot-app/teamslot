package ma.teamslot.events.internal;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import ma.teamslot.events.IdempotentProcessor;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Consommateur idempotent pour les services MongoDB (SCRUM-111).
 * processed_event a pour _id l'eventId : MongoDB refuse un second document avec le même _id.
 * L'insertion est faite AVANT le traitement : dans une transaction MongoDB, une erreur de clé en double
 * annule toute la transaction ; insérer d'abord évite donc d'annuler un traitement déjà fait.
 * Classe interne : les services utilisent l'interface IdempotentProcessor.
 */
class MongoIdempotentProcessor implements IdempotentProcessor {

    static final String COLLECTION = "processed_event";

    private final MongoTemplate mongo;
    private final TransactionTemplate transaction;

    MongoIdempotentProcessor(MongoTemplate mongo, PlatformTransactionManager transactionsMongo) {
        this.mongo = mongo;
        this.transaction = new TransactionTemplate(transactionsMongo);
    }

    @Override
    public boolean traiterUneFois(UUID eventId, Runnable traitement) {
        Objects.requireNonNull(eventId, "eventId est obligatoire");
        Objects.requireNonNull(traitement, "traitement est obligatoire");

        Boolean execute = transaction.execute(status -> {
            try {
                mongo.insert(new Document("_id", eventId.toString()).append("processedAt", new Date()), COLLECTION);
            } catch (DuplicateKeyException deja) {
                // Déjà traité : rien à faire, la transaction (annulée par MongoDB) est abandonnée proprement.
                status.setRollbackOnly();
                return false;
            }
            traitement.run();
            return true;
        });
        return Boolean.TRUE.equals(execute);
    }
}
