package ma.teamslot.servicetemplate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import ma.teamslot.events.EventReader;
import ma.teamslot.events.IdempotentProcessor;
import ma.teamslot.events.Outbox;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Le service utilise libs/events (ADR-015) : les beans viennent de l'auto-configuration,
 * V2__outbox.sql vient du module, et l'enveloppe porte le nom du service.
 * Le comportement détaillé (relais, Kafka, .dlt) est testé dans libs/events.
 */
@SpringBootTest
@ActiveProfiles("test")
class EvenementsTests {

    @Autowired
    private Outbox outbox;

    @Autowired
    private EventReader lecteur;

    @Autowired
    private IdempotentProcessor processeur;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TransactionTemplate transaction;

    @Test
    void les_beans_de_libs_events_sont_disponibles() {
        assertThat(lecteur).isNotNull();
        assertThat(processeur).isNotNull();
    }

    @Test
    void la_migration_v2_du_module_est_appliquee() {
        Integer versionsV2 = jdbc.sql("SELECT count(*) FROM flyway_schema_history WHERE version = '2' AND success")
                .query(Integer.class).single();

        assertThat(versionsV2).isEqualTo(1);
    }

    @Test
    void l_evenement_porte_le_nom_du_service() {
        UUID aggregateId = UUID.randomUUID();

        transaction.executeWithoutResult(status ->
                outbox.record(aggregateId, "match.created", Map.of("matchId", aggregateId.toString())));

        String producer = jdbc.sql("SELECT payload->>'producer' FROM outbox_event WHERE aggregate_id = :id")
                .param("id", aggregateId)
                .query(String.class).single();
        assertThat(producer).isEqualTo("service-template");
    }
}
