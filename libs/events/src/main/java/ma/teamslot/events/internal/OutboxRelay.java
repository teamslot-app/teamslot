package ma.teamslot.events.internal;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relais de l'outbox : envoie à Kafka les événements pas encore publiés, dans l'ordre d'enregistrement.
 * Livraison "au moins une fois" : un doublon est possible, le consommateur idempotent le neutralise.
 * Classe interne : bean créé par EventsAutoConfiguration.
 */
class OutboxRelay {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int TAILLE_LOT = 50;
    // Plus long que delivery.timeout.ms (10 s) : c est l erreur de Kafka, avec sa cause, qui remonte.
    private static final long DELAI_ENVOI_SECONDES = 15;

    // SKIP LOCKED + un seul pod par service : avec plusieurs pods, deux événements du même agrégat pourraient
    // partir dans le désordre (docs/outbox-howto.md).

    /** Lecture des identifiants en texte : UUID natif en PostgreSQL, CHAR(36) en MySQL (SCRUM-147). */
    static final RowMapper<LigneOutbox> LIGNE = (resultat, numero) -> new LigneOutbox(
            UUID.fromString(resultat.getString("id")),
            UUID.fromString(resultat.getString("aggregate_id")),
            resultat.getString("event_type"),
            resultat.getString("payload"));

    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final DialecteSql dialecte;

    OutboxRelay(JdbcClient jdbc, KafkaTemplate<String, String> kafka) {
        this(jdbc, kafka, DialecteSql.POSTGRESQL);
    }

    OutboxRelay(JdbcClient jdbc, KafkaTemplate<String, String> kafka, DialecteSql dialecte) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.dialecte = dialecte;
    }

    /** Toutes les secondes (si la planification est active, voir EventsAutoConfiguration.PlanificationDuRelais). */
    @Scheduled(fixedDelayString = "${teamslot.outbox.relay.delay-ms:1000}")
    @Transactional
    public void publierEnAttente() {
        List<LigneOutbox> lignes = jdbc.sql(dialecte.selectionnerAPublier)
                .param("taille", TAILLE_LOT)
                .query(LIGNE)
                .list();

        for (LigneOutbox ligne : lignes) {
            if (!envoyer(ligne)) {
                // On s'arrête pour garder l'ordre : la suite repartira au prochain passage.
                break;
            }
            jdbc.sql(dialecte.marquerPublie).param("id", dialecte.identifiant(ligne.id())).update();
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
