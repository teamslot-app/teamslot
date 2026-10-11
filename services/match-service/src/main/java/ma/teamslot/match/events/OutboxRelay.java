package ma.teamslot.match.events;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relais de l'outbox : envoie à Kafka les événements pas encore publiés, dans l'ordre d'enregistrement.
 * Livraison "au moins une fois" : un doublon est possible, le consommateur idempotent le neutralise.
 */
@Component
public class OutboxRelay {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int TAILLE_LOT = 50;
    // Plus long que delivery.timeout.ms (10 s) : c est l erreur de Kafka, avec sa cause, qui remonte.
    private static final long DELAI_ENVOI_SECONDES = 15;

    /** SKIP LOCKED + un seul pod par service : avec plusieurs pods, deux événements du même agrégat pourraient partir dans le désordre (docs/outbox-howto.md). */
    private static final String SELECT_A_PUBLIER = """
            SELECT id, aggregate_id, event_type, CAST(payload AS TEXT) AS payload
            FROM outbox_event
            WHERE published_at IS NULL
            ORDER BY position
            LIMIT :taille
            FOR UPDATE SKIP LOCKED
            """;

    private static final String MARQUER_PUBLIE =
            "UPDATE outbox_event SET published_at = now() WHERE id = :id";

    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafka;

    public OutboxRelay(JdbcClient jdbc, KafkaTemplate<String, String> kafka) {
        this.jdbc = jdbc;
        this.kafka = kafka;
    }

    /** Toutes les secondes (si la planification est active, voir OutboxSchedulingConfig). */
    @Scheduled(fixedDelayString = "${teamslot.outbox.relay.delay-ms:1000}")
    @Transactional
    public void publierEnAttente() {
        List<LigneOutbox> lignes = jdbc.sql(SELECT_A_PUBLIER)
                .param("taille", TAILLE_LOT)
                .query(LigneOutbox.class)
                .list();

        for (LigneOutbox ligne : lignes) {
            if (!envoyer(ligne)) {
                // On s'arrête pour garder l'ordre : la suite repartira au prochain passage.
                break;
            }
            jdbc.sql(MARQUER_PUBLIE).param("id", ligne.id()).update();
        }
    }

    /** Envoie une ligne et attend la confirmation de Kafka. Topic = type, clé = aggregateId. */
    private boolean envoyer(LigneOutbox ligne) {
        try {
            kafka.send(ligne.eventType(), ligne.aggregateId().toString(), ligne.payload())
                    .get(DELAI_ENVOI_SECONDES, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException e) {
            // Jamais le payload dans les logs : il peut contenir des données personnelles.
            LOG.warn("Envoi Kafka impossible pour l'événement {} ({}), nouvel essai au prochain passage : {}",
                    ligne.id(), ligne.eventType(), NestedExceptionUtils.getMostSpecificCause(e).getClass().getSimpleName());
            return false;
        }
    }

    record LigneOutbox(UUID id, UUID aggregateId, String eventType, String payload) {
    }
}
