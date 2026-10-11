package ma.teamslot.match;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.kafka.KafkaContainer;

/** SCRUM-62 : payment.completed ou payment.on_site_accepted confirme le match, une seule fois. */
@SpringBootTest(properties = "teamslot.match.consumers.enabled=true")
@ActiveProfiles("test")
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
    @Autowired JdbcClient jdbc;

    /** Pas de DELETE FROM matches : participants (SCRUM-60) y fait reference ; chaque test a ses propres matchs. */
    @BeforeEach
    void preparer() {
        jdbc.sql("DELETE FROM outbox_event").update();
        jdbc.sql("DELETE FROM processed_event").update();
    }

    @Test
    void payment_completed_confirme_le_match_et_publie_match_confirmed() throws Exception {
        UUID matchId = matchEnAttenteDePaiement();

        envoyer("payment.completed", paiement(UUID.randomUUID(), "payment.completed", matchId));

        attendre(() -> "CONFIRMED".equals(statut(matchId)));
        assertThat(statut(matchId)).isEqualTo("CONFIRMED");
        assertThat(version(matchId)).isEqualTo(1L);
        assertThat(jdbc.sql("SELECT event_type FROM outbox_event WHERE aggregate_id = :m")
                .param("m", matchId).query(String.class).single()).isEqualTo("match.confirmed");
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
        assertThat(jdbc.sql("SELECT count(*) FROM outbox_event WHERE aggregate_id = :m")
                .param("m", matchId).query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void paiement_pour_un_match_inconnu_est_ignore_sans_bloquer_les_suivants() throws Exception {
        UUID inconnu = UUID.randomUUID();
        envoyer("payment.completed", paiement(UUID.randomUUID(), "payment.completed", inconnu));
        UUID matchId = matchEnAttenteDePaiement();
        envoyer("payment.completed", paiement(UUID.randomUUID(), "payment.completed", matchId));

        attendre(() -> "CONFIRMED".equals(statut(matchId)));
        assertThat(statut(matchId)).isEqualTo("CONFIRMED");
        assertThat(jdbc.sql("SELECT count(*) FROM outbox_event WHERE aggregate_id = :m")
                .param("m", inconnu).query(Long.class).single()).isZero();
    }

    private UUID matchEnAttenteDePaiement() {
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO matches (id, kind, status, visibility, owner_id, payer_id, venue_id,
                                     slot_id, starts_at, total_amount_cents, currency)
                VALUES (:id, 'FRIENDLY', 'PENDING_PAYMENT', 'PRIVATE', :owner, :owner, :venue,
                        :slot, :debut, 30000, 'MAD')
                """)
                .param("id", id).param("owner", owner)
                .param("venue", UUID.randomUUID()).param("slot", UUID.randomUUID())
                .param("debut", OffsetDateTime.parse("2026-10-20T18:00:00Z"))
                .update();
        return id;
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
        return jdbc.sql("SELECT status FROM matches WHERE id = :id").param("id", matchId)
                .query(String.class).single();
    }

    private long version(UUID matchId) {
        return jdbc.sql("SELECT version FROM matches WHERE id = :id").param("id", matchId)
                .query(Long.class).single();
    }

    /** Attend qu'une condition soit vraie, 30 secondes au maximum. */
    private static void attendre(BooleanSupplier condition) throws InterruptedException {
        long fin = System.currentTimeMillis() + 30_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < fin) {
            Thread.sleep(200);
        }
    }
}
