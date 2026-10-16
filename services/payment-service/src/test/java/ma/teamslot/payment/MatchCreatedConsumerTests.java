package ma.teamslot.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.kafka.KafkaContainer;

/** SCRUM-62 : match.created cree un paiement PENDING, une seule fois (S6), avec un vrai Kafka. */
@SpringBootTest(properties = "teamslot.payment.consumers.enabled=true")
@ActiveProfiles("test")
@Import(KafkaDeTest.class)
class MatchCreatedConsumerTests {

    private static final String TOPIC = "match.created";

    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired KafkaContainer kafka;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void viderLesTables() {
        jdbc.sql("DELETE FROM payments").update();
        jdbc.sql("DELETE FROM processed_event").update();
    }

    @Test
    void match_created_cree_un_paiement_en_attente_avec_le_montant_de_l_evenement() throws Exception {
        UUID matchId = UUID.randomUUID();
        UUID payeur = UUID.randomUUID();

        envoyer(matchCree(UUID.randomUUID(), 1, matchId, payeur, "30000", "\"MAD\"", "true"));

        Attente.jusqua(() -> nombrePaiements() == 1);
        Map<String, Object> p = jdbc.sql("""
                SELECT match_id, payer_id, total_amount_cents, currency, accepts_on_site_payment, status, mode
                FROM payments
                """).query().singleRow();
        assertThat(p).containsEntry("match_id", matchId).containsEntry("payer_id", payeur)
                .containsEntry("total_amount_cents", 30000L).containsEntry("currency", "MAD")
                .containsEntry("accepts_on_site_payment", true).containsEntry("status", "PENDING");
        assertThat(p.get("mode")).isNull();
    }

    @Test
    void s6_le_meme_evenement_rejoue_ne_cree_qu_un_paiement() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        String message = matchCree(eventId, 1, matchId, UUID.randomUUID(), "30000", "\"MAD\"", "false");

        envoyer(message);
        envoyer(message);
        // Un autre evenement pour le meme match (autre eventId) : l'unicite en base protege aussi.
        envoyer(matchCree(UUID.randomUUID(), 1, matchId, UUID.randomUUID(), "1", "\"MAD\"", "true"));
        String temoin = matchCree(UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), "100", "\"MAD\"", "false");
        envoyer(temoin);

        // Une seule partition : quand le temoin est traite, les trois messages precedents l'ont ete.
        Attente.jusqua(() -> nombrePaiements() == 2);
        assertThat(nombrePaiements()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT total_amount_cents FROM payments WHERE match_id = :m")
                .param("m", matchId).query(Long.class).single()).isEqualTo(30000L);
    }

    @Test
    void donnees_invalides_vont_au_dlt_sans_paiement_et_sans_bloquer_les_suivants() throws Exception {
        String sansPayeur = """
                {"eventId":"%s","type":"match.created","version":1,"occurredAt":"2026-10-12T20:02:00Z",
                 "producer":"match-service","data":{"matchId":"%s","totalAmount":30000,"currency":"MAD",
                 "acceptsOnSitePayment":true}}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
        String montantNegatif = matchCree(UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), "-1", "\"MAD\"", "true");
        envoyer(sansPayeur);
        envoyer(montantNegatif);
        envoyer(matchCree(UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), "100", "\"MAD\"", "false"));

        Attente.jusqua(() -> nombrePaiements() == 1);
        assertThat(nombrePaiements()).isEqualTo(1);
        assertThat(lire(TOPIC + ".dlt", 2)).hasSize(2);
    }

    @Test
    void une_autre_version_de_l_evenement_est_ignoree() throws Exception {
        envoyer(matchCree(UUID.randomUUID(), 2, UUID.randomUUID(), UUID.randomUUID(), "30000", "\"MAD\"", "true"));
        envoyer(matchCree(UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), "100", "\"MAD\"", "false"));

        Attente.jusqua(() -> nombrePaiements() == 1);
        assertThat(nombrePaiements()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT total_amount_cents FROM payments").query(Long.class).single()).isEqualTo(100L);
    }

    /** match.created complet (docs/events.md), avec un champ inconnu qui doit etre ignore. */
    private static String matchCree(UUID eventId, int version, UUID matchId, UUID payeur, String montant,
                                    String devise, String surPlace) {
        return """
                {"eventId":"%s","type":"match.created","version":%d,"occurredAt":"2026-10-12T20:02:00Z",
                 "producer":"match-service","data":{"matchId":"%s","kind":"FRIENDLY","visibility":"PRIVATE",
                 "ownerId":"%s","payerId":"%s","venueId":"%s","slotId":"%s","startsAt":"2026-10-20T18:00:00Z",
                 "totalAmount":%s,"currency":%s,"acceptsOnSitePayment":%s,"champInconnu":"ignore"}}
                """.formatted(eventId, version, matchId, payeur, payeur, UUID.randomUUID(), UUID.randomUUID(),
                montant, devise, surPlace);
    }

    private void envoyer(String message) throws Exception {
        kafkaTemplate.send(TOPIC, "cle", message).get(10, TimeUnit.SECONDS);
    }

    private long nombrePaiements() {
        return jdbc.sql("SELECT count(*) FROM payments").query(Long.class).single();
    }

    /** Lit un topic depuis le debut jusqu'a avoir le nombre attendu de messages, 20 secondes au maximum. */
    private List<ConsumerRecord<String, String>> lire(String topic, int attendus) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "lecture-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> recus = new ArrayList<>();
        try (KafkaConsumer<String, String> lecteur = new KafkaConsumer<>(config)) {
            lecteur.subscribe(List.of(topic));
            long fin = System.currentTimeMillis() + 20_000;
            while (recus.size() < attendus && System.currentTimeMillis() < fin) {
                lecteur.poll(Duration.ofMillis(500)).forEach(recus::add);
            }
        }
        return recus;
    }
}
