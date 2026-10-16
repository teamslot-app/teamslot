package ma.teamslot.events.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import ma.teamslot.events.MongoDeTest;
import ma.teamslot.events.Outbox;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;

/** De bout en bout : outbox (MongoDB) -> relais -> Kafka, tous en conteneurs. Critères relais de SCRUM-111. */
@SpringBootTest(properties = {"teamslot.events.store=mongodb", "spring.flyway.enabled=false"})
@Import({MongoDeTest.class, MongoOutboxRelayTests.KafkaDeTest.class})
class MongoOutboxRelayTests {

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
    private MongoOutboxRelay relais;

    @Autowired
    private MongoTemplate mongo;

    @Autowired
    private MongoTransactionManager transactionsMongo;

    @Autowired
    private KafkaContainer kafka;

    private TransactionTemplate transaction;

    @BeforeEach
    void viderLaCollection() {
        mongo.remove(new Query(), MongoOutbox.COLLECTION);
        transaction = new TransactionTemplate(transactionsMongo);
    }

    @Test
    void le_relais_publie_dans_l_ordre_avec_la_cle_et_marque_les_lignes() {
        UUID matchId = UUID.randomUUID();
        enregistrer(matchId, "match.created");
        enregistrer(matchId, "match.created");

        relais.publierEnAttente();

        assertThat(nonPubliees()).isZero();
        List<ConsumerRecord<String, String>> recus = lire("match.created", 2);
        assertThat(recus).hasSize(2);
        assertThat(recus).allSatisfy(recu -> assertThat(recu.key()).isEqualTo(matchId.toString()));
        // Même ordre que l'enregistrement (tri par position)
        List<String> idsEnregistres = mongo.find(new Query().with(Sort.by("position")), Document.class,
                MongoOutbox.COLLECTION).stream().map(ligne -> ligne.getString("_id")).toList();
        assertThat(recus.get(0).value()).contains(idsEnregistres.get(0));
        assertThat(recus.get(1).value()).contains(idsEnregistres.get(1));
    }

    @Test
    void kafka_indisponible_rien_n_est_marque_puis_nouvel_essai() {
        enregistrer(UUID.randomUUID(), "match.created");
        enregistrer(UUID.randomUUID(), "match.created");

        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaEnPanne = mock(KafkaTemplate.class);
        when(kafkaEnPanne.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker indisponible")));
        new MongoOutboxRelay(mongo, kafkaEnPanne).publierEnAttente();
        assertThat(nonPubliees()).isEqualTo(2);

        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaRevenu = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> succes = CompletableFuture.completedFuture(null);
        when(kafkaRevenu.send(anyString(), anyString(), anyString())).thenReturn(succes);
        new MongoOutboxRelay(mongo, kafkaRevenu).publierEnAttente();
        assertThat(nonPubliees()).isZero();
    }

    @Test
    void sans_evenement_le_relais_ne_fait_rien() {
        relais.publierEnAttente();

        assertThat(mongo.getCollection(MongoOutbox.COLLECTION).countDocuments()).isZero();
    }

    private void enregistrer(UUID matchId, String type) {
        transaction.executeWithoutResult(status -> outbox.record(matchId, type, Map.of("matchId", matchId.toString())));
    }

    private long nonPubliees() {
        return mongo.count(new Query(where("publishedAt").is(null)), MongoOutbox.COLLECTION);
    }

    /** Lit un topic depuis le début jusqu'à recevoir le nombre attendu, pendant 15 secondes au maximum. */
    private List<ConsumerRecord<String, String>> lire(String topic, int attendus) {
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
            while (recus.size() < attendus && System.currentTimeMillis() < fin) {
                lecteur.poll(Duration.ofMillis(500)).forEach(recus::add);
            }
        }
        return recus;
    }
}
