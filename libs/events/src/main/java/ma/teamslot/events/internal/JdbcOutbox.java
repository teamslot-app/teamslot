package ma.teamslot.events.internal;

import java.util.Objects;
import java.util.UUID;
import ma.teamslot.events.EventEnvelope;
import ma.teamslot.events.Outbox;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Écrit les événements dans la table outbox_event, dans la transaction de l'appelant (PostgreSQL ou MySQL, SCRUM-147).
 * Classe interne : les services utilisent l'interface Outbox.
 */
class JdbcOutbox implements Outbox {

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;
    private final String producer;
    private final DialecteSql dialecte;

    /** @param producer nom du service émetteur (spring.application.name), champ producer de l'enveloppe */
    JdbcOutbox(JdbcClient jdbc, JsonMapper jsonMapper, String producer) {
        this(jdbc, jsonMapper, producer, DialecteSql.POSTGRESQL);
    }

    JdbcOutbox(JdbcClient jdbc, JsonMapper jsonMapper, String producer, DialecteSql dialecte) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.producer = producer;
        this.dialecte = dialecte;
    }

    /** MANDATORY : refuse d'écrire hors transaction, pour garantir "même transaction que le changement d'état". */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID aggregateId, String type, Object data) {
        Objects.requireNonNull(aggregateId, "aggregateId est obligatoire");
        EventEnvelope<Object> envelope = EventEnvelope.of(type, producer, data);

        jdbc.sql(dialecte.inserer)
                .param("id", dialecte.identifiant(envelope.eventId()))
                .param("aggregateId", dialecte.identifiant(aggregateId))
                .param("type", envelope.type())
                .param("payload", jsonMapper.writeValueAsString(envelope))
                .param("occurredAt", dialecte.horodatage(envelope.occurredAt()))
                .update();
    }
}
