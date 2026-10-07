package ma.teamslot.servicetemplate.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;

/** Test de bout en bout : outbox (PostgreSQL) -> relais -> Kafka, tous en conteneurs. */
@SpringBootTest
@ActiveProfiles("test")
class OutboxRelayTests {

    @TestConfiguration(proxyBeanMethods = false)
    static class KafkaDeTest {
        @Bean
        @ServiceConnection
        KafkaContainer kafka() {
            return new KafkaContainer("apache/kafka:4.1.0");
        }
    }

    @Autowired
    private Outbox outbox;

    @Autowired
    private OutboxRelay relais;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private KafkaContainer kafka;

    @BeforeEach
    void viderLaTable() {
        jdbc.sql("DELETE FROM outbox_event").update();
    }

    @Test
    void le_relais_publie_dans_le_topic_avec_la_cle_et_marque_la_ligne() {
        UUID matchId = UUID.randomUUID();
        transaction.executeWithoutResult(status ->
                outbox.record(matchId, "match.created", Map.of("matchId", matchId.toString())));

        relais.publierEnAttente();

        long nonPublies = jdbc.sql("SELECT count(*) FROM outbox_event WHERE published_at IS NULL")
                .query(Long.class).single();
        assertThat(nonPublies).isZero();

        List<ConsumerRecord<String, String>> recus = lire("match.created");
        assertThat(recus).hasSize(1);
        assertThat(recus.get(0).key()).isEqualTo(matchId.toString());
        assertThat(recus.get(0).value())
                .contains("match.created")
                .contains("service-template")
                .contains(matchId.toString());
    }

    @Test
    void sans_evenement_le_relais_ne_fait_rien() {
        relais.publierEnAttente();

        long total = jdbc.sql("SELECT count(*) FROM outbox_event").query(Long.class).single();
        assertThat(total).isZero();
    }

    /** Lit un topic depuis le début, pendant 15 secondes au maximum. */
    private List<ConsumerRecord<String, String>> lire(String topic) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        List<ConsumerRecord<String, String>> recus = new ArrayList<>();
        try (KafkaConsumer<String, String> lecteur = new KafkaConsumer<>(config)) {
            lecteur.subscribe(List.of(topic));
            long fin = System.currentTimeMillis() + 15_000;
            while (recus.isEmpty() && System.currentTimeMillis() < fin) {
                lecteur.poll(Duration.ofMillis(500)).forEach(recus::add);
            }
        }
        return recus;
    }
}
