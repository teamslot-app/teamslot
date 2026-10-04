package ma.teamslot.servicetemplate.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** Tests de l'outbox sur un vrai PostgreSQL (Testcontainers, profil test). */
@SpringBootTest
@ActiveProfiles("test")
class JdbcOutboxTests {

    @Autowired
    private Outbox outbox;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TransactionTemplate transaction;

    @BeforeEach
    void viderLaTable() {
        jdbc.sql("DELETE FROM outbox_event").update();
    }

    @Test
    void record_ecrit_l_enveloppe_complete() {
        UUID matchId = UUID.randomUUID();

        transaction.executeWithoutResult(status ->
                outbox.record(matchId, "match.created", Map.of("matchId", matchId.toString())));

        Map<String, Object> ligne = jdbc.sql("""
                SELECT aggregate_id, event_type, published_at,
                       CAST(id AS TEXT)             AS id,
                       payload->>'eventId'          AS event_id,
                       payload->>'producer'         AS producer,
                       payload->>'occurredAt'       AS occurred_at,
                       payload->'data'->>'matchId'  AS data_match_id
                FROM outbox_event
                """).query().singleRow();

        assertThat(ligne.get("aggregate_id")).isEqualTo(matchId);
        assertThat(ligne.get("event_type")).isEqualTo("match.created");
        assertThat(ligne.get("published_at")).isNull();
        assertThat(ligne.get("event_id")).isEqualTo(ligne.get("id"));
        assertThat(ligne.get("producer")).isEqualTo("service-template");
        assertThat((String) ligne.get("occurred_at")).contains("T").endsWith("Z");
        assertThat(ligne.get("data_match_id")).isEqualTo(matchId.toString());
    }

    @Test
    void transaction_annulee_aucun_evenement() {
        transaction.executeWithoutResult(status -> {
            outbox.record(UUID.randomUUID(), "match.created", Map.of());
            status.setRollbackOnly();
        });

        assertThat(nombreEvenements()).isZero();
    }

    @Test
    void refuse_un_appel_hors_transaction() {
        UUID matchId = UUID.randomUUID();
        Map<String, String> data = Map.of();

        assertThatThrownBy(() -> outbox.record(matchId, "match.created", data))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(nombreEvenements()).isZero();
    }

    private long nombreEvenements() {
        return jdbc.sql("SELECT count(*) FROM outbox_event").query(Long.class).single();
    }
}
