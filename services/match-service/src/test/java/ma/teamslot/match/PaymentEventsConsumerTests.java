package ma.teamslot.match;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.kafka.KafkaContainer;

/** SCRUM-62 : payment.completed ou payment.on_site_accepted confirme le match, une seule fois. */
@SpringBootTest(properties = "teamslot.match.consumers.enabled=true")
@ActiveProfiles("test")
@Import(MongoDeTest.class)
class PaymentEventsConsumerTests {

    @TestConfiguration(proxyBeanMethods = false)
    static class KafkaDeTest {
        @Bean
        @ServiceConnection
        KafkaContainer kafka() {
            return new KafkaContainer("apache/kafka:4.1.0");
        }
    }

    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired MongoTemplate mongo;

    /** Les matchs restent : chaque test a ses propres matchs. */
    @BeforeEach
    void preparer() {
        donnees().viderEvenements();
    }

    private DonneesMongo donnees() {
        return new DonneesMongo(mongo);
    }

    @Test
    void payment_completed_confirme_le_match_et_publie_match_confirmed() throws Exception {
        UUID matchId = matchEnAttenteDePaiement();

        envoyer("payment.completed", paiement(UUID.randomUUID(), "payment.completed", matchId));

        attendre(() -> "CONFIRMED".equals(statut(matchId)));
        assertThat(statut(matchId)).isEqualTo("CONFIRMED");
        assertThat(version(matchId)).isEqualTo(1L);
        assertThat(donnees().evenementsDe(matchId)).singleElement()
                .satisfies(e -> assertThat(e.getString("eventType")).isEqualTo("match.confirmed"));
    }

    @Test
    void payment_on_site_accepted_confirme_aussi_le_match() throws Exception {
        UUID matchId = matchEnAttenteDePaiement();

        envoyer("payment.on_site_accepted", paiement(UUID.randomUUID(), "payment.on_site_accepted", matchId));

        attendre(() -> "CONFIRMED".equals(statut(matchId)));
        assertThat(statut(matchId)).isEqualTo("CONFIRMED");
    }

    @Test
    void evenement_rejoue_ou_deuxieme_paiement_un_seul_match_confirmed() throws Exception {
        UUID matchId = matchEnAttenteDePaiement();
        String message = paiement(UUID.randomUUID(), "payment.completed", matchId);

        envoyer("payment.completed", message);
        envoyer("payment.completed", message);
        envoyer("payment.completed", paiement(UUID.randomUUID(), "payment.completed", matchId));
        UUID temoin = matchEnAttenteDePaiement();
        envoyer("payment.completed", paiement(UUID.randomUUID(), "payment.completed", temoin));

        // Une seule partition : quand le temoin est confirme, les trois messages precedents ont ete traites.
        attendre(() -> "CONFIRMED".equals(statut(temoin)));
        assertThat(version(matchId)).isEqualTo(1L);
        assertThat(donnees().evenementsDe(matchId)).hasSize(1);
    }

    @Test
    void paiement_pour_un_match_inconnu_est_ignore_sans_bloquer_les_suivants() throws Exception {
        UUID inconnu = UUID.randomUUID();
        envoyer("payment.completed", paiement(UUID.randomUUID(), "payment.completed", inconnu));
        UUID matchId = matchEnAttenteDePaiement();
        envoyer("payment.completed", paiement(UUID.randomUUID(), "payment.completed", matchId));

        attendre(() -> "CONFIRMED".equals(statut(matchId)));
        assertThat(statut(matchId)).isEqualTo("CONFIRMED");
        assertThat(donnees().evenementsDe(inconnu)).isEmpty();
    }

    private UUID matchEnAttenteDePaiement() {
        return donnees().creerMatch(UUID.randomUUID(), "PENDING_PAYMENT");
    }

    /** Evenement de paiement (docs/events.md), avec les champs que match ne lit pas. */
    private static String paiement(UUID eventId, String type, UUID matchId) {
        return """
                {"eventId":"%s","type":"%s","version":1,"occurredAt":"2026-10-12T20:05:00Z",
                 "producer":"payment-service","data":{"matchId":"%s","paymentId":"%s","payerId":"%s",
                 "mode":"ONLINE","totalAmount":30000,"currency":"MAD"}}
                """.formatted(eventId, type, matchId, UUID.randomUUID(), UUID.randomUUID());
    }

    private void envoyer(String topic, String message) throws Exception {
        kafkaTemplate.send(topic, "cle", message).get(10, TimeUnit.SECONDS);
    }

    private String statut(UUID matchId) {
        return donnees().match(matchId).getString("status");
    }

    private long version(UUID matchId) {
        return ((Number) donnees().match(matchId).get("version")).longValue();
    }

    /** Attend qu'une condition soit vraie, 30 secondes au maximum. */
    private static void attendre(BooleanSupplier condition) throws InterruptedException {
        long fin = System.currentTimeMillis() + 30_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < fin) {
            Thread.sleep(200);
        }
    }
}
