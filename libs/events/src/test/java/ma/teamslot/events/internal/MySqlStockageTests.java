package ma.teamslot.events.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * SCRUM-147 — le SQL MySQL de l'outbox, du relais et de l'idempotence, sur un vrai MySQL 8.4 (Testcontainers).
 * Tables : src/test/resources/mysql/outbox-mysql.sql, copie de la migration V5 d'identity-service.
 * Kafka est remplacé par un faux : ici, on teste les requêtes, pas le broker (déjà couvert par OutboxRelayTests).
 */
class MySqlStockageTests {

    private static final DriverManagerDataSource BASE = new DriverManagerDataSource(
            "jdbc:tc:mysql:8.4:///events?TC_DAEMON=true&TC_INITSCRIPT=mysql/outbox-mysql.sql", "test", "test");

    static {
        BASE.setDriverClassName("org.testcontainers.jdbc.ContainerDatabaseDriver");
    }

    private final JdbcClient jdbc = JdbcClient.create(BASE);
    private final TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(BASE));
    private final JdbcOutbox outbox =
            new JdbcOutbox(jdbc, JsonMapper.builder().build(), "identity-service", DialecteSql.MYSQL);
    private final JdbcIdempotentProcessor processeur = new JdbcIdempotentProcessor(jdbc, DialecteSql.MYSQL);

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final OutboxRelay relais = new OutboxRelay(jdbc, kafka, DialecteSql.MYSQL);

    @BeforeEach
    void viderLesTables() {
        jdbc.sql("DELETE FROM outbox_event").update();
        jdbc.sql("DELETE FROM processed_event").update();
    }

    @Test
    void record_ecrit_l_enveloppe_en_json_avec_des_identifiants_texte() {
        UUID userId = UUID.randomUUID();

        transaction.executeWithoutResult(statut ->
                outbox.record(userId, "user.registered", Map.of("userId", userId.toString())));

        Map<String, Object> ligne = jdbc.sql("""
                SELECT id, aggregate_id, event_type, published_at,
                       JSON_UNQUOTE(JSON_EXTRACT(payload, '$.producer'))    AS producer,
                       JSON_UNQUOTE(JSON_EXTRACT(payload, '$.eventId'))     AS event_id,
                       JSON_UNQUOTE(JSON_EXTRACT(payload, '$.data.userId')) AS data_user_id
                FROM outbox_event
                """).query().singleRow();
        assertThat(ligne.get("aggregate_id")).isEqualTo(userId.toString());
        assertThat(ligne.get("event_type")).isEqualTo("user.registered");
        assertThat(ligne.get("producer")).isEqualTo("identity-service");
        assertThat(ligne.get("event_id")).isEqualTo(ligne.get("id"));
        assertThat(ligne.get("data_user_id")).isEqualTo(userId.toString());
        assertThat(ligne.get("published_at")).isNull();
    }

    @Test
    void le_relais_publie_dans_l_ordre_puis_marque_publie() {
        UUID premier = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        transaction.executeWithoutResult(statut -> {
            outbox.record(premier, "user.registered", Map.of("n", 1));
            outbox.record(second, "identity.email_verification_requested", Map.of("n", 2));
        });
        CompletableFuture<SendResult<String, String>> confirme = CompletableFuture.completedFuture(null);
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(confirme);

        transaction.executeWithoutResult(statut -> relais.publierEnAttente());

        InOrder ordre = inOrder(kafka);
        ordre.verify(kafka).send(eq("user.registered"), eq(premier.toString()), anyString());
        ordre.verify(kafka).send(eq("identity.email_verification_requested"), eq(second.toString()), anyString());
        assertThat(jdbc.sql("SELECT COUNT(*) FROM outbox_event WHERE published_at IS NULL").query(Long.class).single())
                .isZero();
    }

    @Test
    void un_evenement_recu_deux_fois_n_est_traite_qu_une_fois() {
        UUID eventId = UUID.randomUUID();
        AtomicInteger traitements = new AtomicInteger();

        boolean premiereFois = transaction.execute(statut ->
                processeur.traiterUneFois(eventId, traitements::incrementAndGet));
        boolean deuxiemeFois = transaction.execute(statut ->
                processeur.traiterUneFois(eventId, traitements::incrementAndGet));

        assertThat(premiereFois).isTrue();
        assertThat(deuxiemeFois).isFalse();
        assertThat(traitements).hasValue(1);
    }

    @Test
    void la_propriete_choisit_le_dialecte() {
        MockEnvironment environnement = new MockEnvironment();
        assertThat(DialecteSql.depuis(environnement)).isEqualTo(DialecteSql.POSTGRESQL);
        environnement.setProperty(DialecteSql.PROPRIETE, "MySQL");
        assertThat(DialecteSql.depuis(environnement)).isEqualTo(DialecteSql.MYSQL);
    }
}
