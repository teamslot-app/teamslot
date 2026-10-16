package ma.teamslot.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * SCRUM-76 : match.cancelled rembourse un paiement en ligne (COMPLETED), une seule fois (S6), avec un vrai Kafka.
 * PENDING et ON_SITE_ACCEPTED ne sont pas remboursés ; le montant est celui de la base.
 */
@SpringBootTest(properties = "teamslot.payment.consumers.enabled=true")
@ActiveProfiles("test")
@Import(KafkaDeTest.class)
class MatchCancelledConsumerTests {

    private static final String TOPIC = "match.cancelled";

    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void viderLesTables() {
        jdbc.sql("DELETE FROM payments").update();
        jdbc.sql("DELETE FROM processed_event").update();
        jdbc.sql("DELETE FROM outbox_event").update();
    }

    @Test
    void un_paiement_en_ligne_est_rembourse_et_payment_refunded_publie_avec_le_montant_de_la_base() throws Exception {
        UUID matchId = paiement("COMPLETED", "ONLINE", 30000);

        // Un champ « amount » dans l'événement doit être ignoré : le montant vient de la base.
        envoyer(matchAnnule(UUID.randomUUID(), 1, matchId, ",\"amount\":1"));

        Attente.jusqua(() -> "REFUNDED".equals(statut(matchId)));
        assertThat(statut(matchId)).isEqualTo("REFUNDED");
        assertThat(remboursements(matchId)).isEqualTo(1L);
        assertThat(jdbc.sql("""
                SELECT payload->'data'->>'amount' FROM outbox_event
                WHERE aggregate_id = :m AND event_type = 'payment.refunded'
                """).param("m", matchId).query(String.class).single()).isEqualTo("30000");
    }

    @Test
    void s6_le_meme_match_cancelled_rejoue_ne_rembourse_qu_une_fois() throws Exception {
        UUID matchId = paiement("COMPLETED", "ONLINE", 30000);
        String message = matchAnnule(UUID.randomUUID(), 1, matchId, "");

        envoyer(message);
        envoyer(message);
        // Autre eventId pour le même match : le paiement est déjà REFUNDED, aucun second remboursement.
        envoyer(matchAnnule(UUID.randomUUID(), 1, matchId, ""));
        UUID temoin = paiement("COMPLETED", "ONLINE", 100);
        envoyer(matchAnnule(UUID.randomUUID(), 1, temoin, ""));

        // Une seule partition : quand le témoin est remboursé, les trois messages précédents ont été traités.
        Attente.jusqua(() -> "REFUNDED".equals(statut(temoin)));
        assertThat(remboursements(matchId)).isEqualTo(1L);
        assertThat(statut(matchId)).isEqualTo("REFUNDED");
    }

    @Test
    void pending_et_paiement_sur_place_ne_sont_pas_rembourses() throws Exception {
        UUID enAttente = paiement("PENDING", null, 30000);
        UUID surPlace = paiement("ON_SITE_ACCEPTED", "ON_SITE", 30000);
        UUID inconnu = UUID.randomUUID();
        envoyer(matchAnnule(UUID.randomUUID(), 1, enAttente, ""));
        envoyer(matchAnnule(UUID.randomUUID(), 1, surPlace, ""));
        envoyer(matchAnnule(UUID.randomUUID(), 1, inconnu, ""));
        UUID temoin = paiement("COMPLETED", "ONLINE", 100);
        envoyer(matchAnnule(UUID.randomUUID(), 1, temoin, ""));

        Attente.jusqua(() -> "REFUNDED".equals(statut(temoin)));
        assertThat(statut(enAttente)).isEqualTo("PENDING");
        assertThat(statut(surPlace)).isEqualTo("ON_SITE_ACCEPTED");
        assertThat(jdbc.sql("SELECT count(*) FROM outbox_event WHERE event_type = 'payment.refunded'")
                .query(Long.class).single()).isEqualTo(1L);
    }

    /** Paiement d'un nouveau match, dans l'état demandé. @return matchId */
    private UUID paiement(String statut, String mode, long montant) {
        UUID matchId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO payments (id, match_id, payer_id, total_amount_cents, currency,
                                      accepts_on_site_payment, mode, status)
                VALUES (:id, :match, :payeur, :montant, 'MAD', true, :mode, :statut)
                """)
                .param("id", UUID.randomUUID()).param("match", matchId).param("payeur", UUID.randomUUID())
                .param("montant", montant).param("mode", mode, java.sql.Types.VARCHAR).param("statut", statut)
                .update();
        return matchId;
    }

    /** match.cancelled (docs/events.md : matchId, reason, cancelledBy), avec un éventuel champ en plus. */
    private static String matchAnnule(UUID eventId, int version, UUID matchId, String enPlus) {
        return """
                {"eventId":"%s","type":"match.cancelled","version":%d,"occurredAt":"2026-10-16T19:30:00Z",
                 "producer":"match-service","data":{"matchId":"%s","reason":"ORGANIZER_CANCELLED",
                 "cancelledBy":"%s"%s}}
                """.formatted(eventId, version, matchId, UUID.randomUUID(), enPlus);
    }

    private void envoyer(String message) throws Exception {
        kafkaTemplate.send(TOPIC, "cle", message).get(10, TimeUnit.SECONDS);
    }

    private String statut(UUID matchId) {
        return jdbc.sql("SELECT status FROM payments WHERE match_id = :m").param("m", matchId)
                .query(String.class).single();
    }

    private long remboursements(UUID matchId) {
        return jdbc.sql("SELECT count(*) FROM outbox_event WHERE aggregate_id = :m AND event_type = 'payment.refunded'")
                .param("m", matchId).query(Long.class).single();
    }
}
