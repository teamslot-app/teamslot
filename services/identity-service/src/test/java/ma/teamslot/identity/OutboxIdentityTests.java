package ma.teamslot.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import ma.teamslot.events.Outbox;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * SCRUM-147 — identity-service a son outbox (libs/events en mode MySQL) : la migration V5 crée les tables,
 * un événement s'écrit dans la transaction, et jamais en dehors.
 */
@SpringBootTest
@ActiveProfiles("test")
class OutboxIdentityTests {

    @Autowired
    private Outbox outbox;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TransactionTemplate transaction;

    @Test
    void un_evenement_est_ecrit_dans_l_outbox_mysql() {
        UUID userId = UUID.randomUUID();

        transaction.executeWithoutResult(statut ->
                outbox.record(userId, "user.registered", Map.of("userId", userId.toString())));

        assertThat(jdbc.sql("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(payload, '$.producer'))
                FROM outbox_event WHERE aggregate_id = :id
                """).param("id", userId.toString()).query(String.class).single())
                .isEqualTo("identity-service");
    }

    @Test
    void hors_transaction_l_outbox_refuse_d_ecrire() {
        assertThatThrownBy(() -> outbox.record(UUID.randomUUID(), "user.registered", Map.of()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
