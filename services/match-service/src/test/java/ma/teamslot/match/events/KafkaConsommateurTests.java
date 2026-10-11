package ma.teamslot.match.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
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
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;

/**
 * Critères Jira de SCRUM-70, avec un vrai Kafka :
 * - un événement reçu deux fois n'a qu'un effet ;
 * - un message invalide part dans <topic>.dlt sans bloquer les suivants.
 */
@SpringBootTest
@ActiveProfiles("test")
class KafkaConsommateurTests {

    private static final String TOPIC = "match.created";

    /** Joue le rôle de payment-service : lit match.created, traite chaque événement une seule fois. */
    public static class ConsommateurDeTest {
        private final EventReader lecteur;
        private final IdempotentProcessor processeur;
        private final AtomicInteger effets;

        ConsommateurDeTest(EventReader lecteur, IdempotentProcessor processeur, AtomicInteger effets) {
            this.lecteur = lecteur;
            this.processeur = processeur;
            this.effets = effets;
        }

        @KafkaListener(topics = TOPIC, groupId = "test-consommateur")
        public void recevoir(String message) {
            EventEnvelope<JsonNode> enveloppe = lecteur.lire(message);
            processeur.traiterUneFois(enveloppe.eventId(), effets::incrementAndGet);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        @Bean
        @ServiceConnection
        KafkaContainer kafka() {
            return new KafkaContainer("apache/kafka:4.1.0");
        }

        @Bean
        AtomicInteger effets() {
            return new AtomicInteger();
        }

        @Bean
        ConsommateurDeTest consommateurDeTest(EventReader lecteur, IdempotentProcessor processeur,
                                              AtomicInteger effets) {
            return new ConsommateurDeTest(lecteur, processeur, effets);
        }
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private AtomicInteger effets;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void remettreAZero() {
        jdbc.sql("DELETE FROM processed_event").update();
        effets.set(0);
    }

    @Test
    void un_evenement_recu_deux_fois_n_a_qu_un_effet() throws Exception {
        String evenementA = valide(UUID.randomUUID());
        envoyer(evenementA);
        envoyer(evenementA);
        envoyer(valide(UUID.randomUUID()));

        // Une seule partition : quand le 3e est traité, les deux copies de A l'ont été aussi.
        attendre(() -> effets.get() >= 2);
        assertThat(effets).hasValue(2);
    }

    @Test
    void un_message_invalide_part_dans_le_dlt_sans_bloquer_les_suivants() throws Exception {
        String invalide = "{ceci n'est pas du json";
        envoyer(invalide);
        envoyer(valide(UUID.randomUUID()));

        attendre(() -> effets.get() >= 1);
        assertThat(effets).hasValue(1);

        List<ConsumerRecord<String, String>> dlt = lire(TOPIC + KafkaErreursConfig.SUFFIXE_DLT);
        assertThat(dlt).hasSize(1);
        assertThat(dlt.get(0).value()).isEqualTo(invalide);
    }

    private static String valide(UUID eventId) {
        return """
                {"eventId":"%s","type":"match.created","version":1,"occurredAt":"2026-10-12T20:02:00Z",
                 "producer":"match-service","data":{"matchId":"%s"}}
                """.formatted(eventId, UUID.randomUUID());
    }

    private void envoyer(String message) throws Exception {
        kafkaTemplate.send(TOPIC, "cle-de-test", message).get(10, TimeUnit.SECONDS);
    }

    /** Attend qu'une condition soit vraie, 30 secondes au maximum. */
    private static void attendre(BooleanSupplier condition) throws InterruptedException {
        long fin = System.currentTimeMillis() + 30_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < fin) {
            Thread.sleep(200);
        }
    }

    /** Lit un topic depuis le début, 15 secondes au maximum. */
    private List<ConsumerRecord<String, String>> lire(String topic) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "lecture-" + UUID.randomUUID(),
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
