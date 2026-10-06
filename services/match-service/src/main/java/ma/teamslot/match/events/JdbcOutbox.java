package ma.teamslot.match.events;

import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Écrit les événements dans la table outbox_event, dans la transaction de l'appelant. */
@Component
public class JdbcOutbox implements Outbox {

    private static final String INSERT = """
            INSERT INTO outbox_event (id, aggregate_id, event_type, payload, occurred_at)
            VALUES (:id, :aggregateId, :type, CAST(:payload AS JSONB), :occurredAt)
            """;

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;
    private final String producer;

    public JdbcOutbox(JdbcClient jdbc, JsonMapper jsonMapper,
                      @Value("${spring.application.name}") String producer) {
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