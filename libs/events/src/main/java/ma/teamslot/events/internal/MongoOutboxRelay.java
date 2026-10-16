package ma.teamslot.events.internal;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.mongodb.client.model.Indexes;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Relais de l'outbox MongoDB (SCRUM-111) : même règles que OutboxRelay (PostgreSQL).
 * Envoie à Kafka les événements pas encore publiés, dans l'ordre d'enregistrement, puis remplit publishedAt.
 * Livraison "au moins une fois" : un doublon est possible, le consommateur idempotent le neutralise.
 * Un seul pod par service (docs/outbox-howto.md). Classe interne : bean créé par EventsAutoConfiguration.
 */
class MongoOutboxRelay implements InitializingBean {

    private static final Logger LOG = LoggerFactory.getLogger(MongoOutboxRelay.class);
    private static final int TAILLE_LOT = 50;
    // Plus long que delivery.timeout.ms (10 s) : c'est l'erreur de Kafka, avec sa cause, qui remonte.
    private static final long DELAI_ENVOI_SECONDES = 15;

    private final MongoTemplate mongo;
    private final KafkaTemplate<String, String> kafka;

    MongoOutboxRelay(MongoTemplate mongo, KafkaTemplate<String, String> kafka) {
        this.mongo = mongo;
        this.kafka = kafka;
    }

    /** Index de la recherche du relais : événements non publiés, triés par position. */
    @Override
    public void afterPropertiesSet() {
        mongo.getCollection(MongoOutbox.COLLECTION).createIndex(Indexes.ascending("publishedAt", "position"));
    }

    @Scheduled(fixedDelayString = "${teamslot.outbox.relay.delay-ms:1000}")
    public void publierEnAttente() {
        List<Document> lot = mongo.find(
                query(where("publishedAt").is(null)).with(Sort.by(Sort.Direction.ASC, "position")).limit(TAILLE_LOT),
                Document.class, MongoOutbox.COLLECTION);

        for (Document ligne : lot) {
            if (!envoyer(ligne)) {
                // On s'arrête pour garder l'ordre : la suite repartira au prochain passage.
                break;
            }
            mongo.updateFirst(query(where("_id").is(ligne.getString("_id"))),
                    Update.update("publishedAt", new Date()), MongoOutbox.COLLECTION);
        }
    }

    /** Envoie une ligne et attend la confirmation de Kafka. Topic = type, clé = aggregateId. */
    private boolean envoyer(Document ligne) {
        String id = ligne.getString("_id");
        String type = ligne.getString("eventType");
        try {
            kafka.send(type, ligne.getString("aggregateId"), ligne.getString("payload"))
                    .get(DELAI_ENVOI_SECONDES, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException e) {
            // Jamais le payload dans les logs : il peut contenir des données personnelles.
            LOG.warn("Envoi Kafka impossible pour l'événement {} ({}), nouvel essai au prochain passage : {}",
                    id, type, NestedExceptionUtils.getMostSpecificCause(e).getClass().getSimpleName());
            return false;
        }
    }
}
