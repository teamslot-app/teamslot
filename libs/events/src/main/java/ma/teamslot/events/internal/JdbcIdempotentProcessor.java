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

    private final JdbcClient jdbc;
    private final DialecteSql dialecte;

    JdbcIdempotentProcessor(JdbcClient jdbc) {
        this(jdbc, DialecteSql.POSTGRESQL);
    }

    JdbcIdempotentProcessor(JdbcClient jdbc, DialecteSql dialecte) {
        this.jdbc = jdbc;
        this.dialecte = dialecte;
    }

    @Override
    @Transactional
    public boolean traiterUneFois(UUID eventId, Runnable traitement) {
        Objects.requireNonNull(eventId, "eventId est obligatoire");
        Objects.requireNonNull(traitement, "traitement est obligatoire");

        int inseres = jdbc.sql(dialecte.marquerTraite).param("eventId", dialecte.identifiant(eventId)).update();
        if (inseres == 0) {
            return false;
        }
        traitement.run();
        return true;
    }
}
