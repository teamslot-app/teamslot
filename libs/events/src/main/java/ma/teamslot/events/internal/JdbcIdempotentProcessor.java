package ma.teamslot.events.internal;

import java.util.Objects;
import java.util.UUID;
import ma.teamslot.events.IdempotentProcessor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consommateur idempotent pour les services PostgreSQL (code inchangé depuis SCRUM-70).
 * Classe interne : les services utilisent l'interface IdempotentProcessor.
 */
class JdbcIdempotentProcessor implements IdempotentProcessor {

    private static final String MARQUER_TRAITE = """
            INSERT INTO processed_event (event_id) VALUES (:eventId)
            ON CONFLICT (event_id) DO NOTHING
            """;

    private final JdbcClient jdbc;

    JdbcIdempotentProcessor(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public boolean traiterUneFois(UUID eventId, Runnable traitement) {
        Objects.requireNonNull(eventId, "eventId est obligatoire");
        Objects.requireNonNull(traitement, "traitement est obligatoire");

        int inseres = jdbc.sql(MARQUER_TRAITE).param("eventId", eventId).update();
        if (inseres == 0) {
            return false;
        }
        traitement.run();
        return true;
    }
}
