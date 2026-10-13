package ma.teamslot.events.internal;

import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import ma.teamslot.events.EventEnvelope;
import ma.teamslot.events.Outbox;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Écrit les événements dans la table outbox_event, dans la transaction de l'appelant.
 * Classe interne : les services utilisent l'interface Outbox.
 */
class JdbcOutbox implements Outbox {

    private static final String INSERT = """
            INSERT INTO outbox_event (id, aggregate_id, event_type, payload, occurred_at)
            VALUES (:id, :aggregateId, :type, CAST(:payload AS JSONB), :occurredAt)
            """;

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;
    private final String producer;

    /** @param producer nom du service émetteur (spring.application.name), champ producer de l'enveloppe */
    JdbcOutbox(JdbcClient jdbc, JsonMapper jsonMapper, String producer) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.producer = producer;
    }

    /** MANDATORY : refuse d'écrire hors transaction, pour garantir "même transaction que le changement d'état". */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID aggregateId, String type, Object data) {
        Objects.requireNonNull(aggregateId, "aggregateId est obligatoire");
        EventEnvelope<Object> envelope = EventEnvelope.of(type, producer, data);

        jdbc.sql(INSERT)
                .param("id", envelope.eventId())
                .param("aggregateId", aggregateId)
                .param("type", envelope.type())
                .param("payload", jsonMapper.writeValueAsString(envelope))
                .param("occurredAt", envelope.occurredAt().atOffset(ZoneOffset.UTC))
                .update();
    }
}
