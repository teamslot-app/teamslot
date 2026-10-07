package ma.teamslot.servicetemplate.events;

import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consommateur idempotent : un événement reçu deux fois n'a qu'un seul effet.
 * L'eventId est enregistré dans processed_event DANS LA MÊME transaction que le traitement :
 * si le traitement échoue, l'enregistrement est annulé et l'événement pourra être retraité.
 */
@Component
public class IdempotentProcessor {

    private static final String MARQUER_TRAITE = """
            INSERT INTO processed_event (event_id) VALUES (:eventId)
            ON CONFLICT (event_id) DO NOTHING
            """;

    private final JdbcClient jdbc;

    public IdempotentProcessor(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @return true si le traitement a été exécuté, false si l'événement avait déjà été traité. */
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
