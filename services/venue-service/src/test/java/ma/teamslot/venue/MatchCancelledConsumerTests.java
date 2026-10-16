package ma.teamslot.venue;

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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.kafka.KafkaContainer;

/**
 * SCRUM-76 : match.cancelled libère le créneau (RESERVED -> RELEASED) et publie slot.released,
 * une seule fois par événement, avec un vrai Kafka. Réservation introuvable : ignorée, sans bloquer.
 */
@SpringBootTest(properties = "teamslot.venue.consumers.enabled=true")
@ActiveProfiles("test")
class MatchCancelledConsumerTests {

  private static final String TOPIC = "match.cancelled";
  private static final UUID VENUE = UUID.fromString("a1000000-0000-4000-8000-000000000001");

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

  private int prochainCreneau;

  @BeforeEach
  void preparer() {
    jdbc.sql("DELETE FROM slot_reservation_equipment").update();
    jdbc.sql("DELETE FROM slot_reservation").update();
    jdbc.sql("DELETE FROM slot").update();
    jdbc.sql("DELETE FROM equipment").update();
    jdbc.sql("DELETE FROM venue").update();
    jdbc.sql("DELETE FROM outbox_event").update();
    jdbc.sql("DELETE FROM processed_event").update();
    jdbc.sql(
            """
            INSERT INTO venue (id, owner_id, name, latitude, longitude, price_per_hour_cents, currency, accepts_on_site_payment)
            VALUES (:id, :owner, 'Terrain test', 31.63, -8.01, 15000, 'MAD', true)
            """)
        .param("id", VENUE)
        .param("owner", UUID.randomUUID())
        .update();
    prochainCreneau = 0;
  }

  @Test
  void le_creneau_est_libere_et_slot_released_publie() throws Exception {
    UUID matchId = UUID.randomUUID();
    UUID creneau = creneauReservePour(matchId);

    envoyer(matchAnnule(UUID.randomUUID(), 1, matchId));

    attendre(() -> "RELEASED".equals(statutReservation(matchId)));
    assertThat(statutReservation(matchId)).isEqualTo("RELEASED");
    assertThat(liberations(matchId)).isEqualTo(1L);
    assertThat(
            jdbc.sql(
                    """
                    SELECT payload->'data'->>'slotId' FROM outbox_event
                    WHERE event_type = 'slot.released' AND payload->'data'->>'matchId' = :m
                    """)
                .param("m", matchId.toString())
                .query(String.class)
                .single())
        .isEqualTo(creneau.toString());
  }

  @Test
  void le_meme_evenement_rejoue_ne_libere_qu_une_fois() throws Exception {
    UUID matchId = UUID.randomUUID();
    creneauReservePour(matchId);
    String message = matchAnnule(UUID.randomUUID(), 1, matchId);

    envoyer(message);
    envoyer(message);
    // Autre eventId pour le même match : déjà RELEASED, aucun second slot.released.
    envoyer(matchAnnule(UUID.randomUUID(), 1, matchId));
    UUID temoin = UUID.randomUUID();
    creneauReservePour(temoin);
    envoyer(matchAnnule(UUID.randomUUID(), 1, temoin));

    // Une seule partition : quand le témoin est libéré, les trois messages précédents ont été traités.
    attendre(() -> "RELEASED".equals(statutReservation(temoin)));
    assertThat(liberations(matchId)).isEqualTo(1L);
  }

  @Test
  void reservation_introuvable_ignoree_sans_bloquer_les_suivants() throws Exception {
    envoyer(matchAnnule(UUID.randomUUID(), 1, UUID.randomUUID()));
    UUID matchId = UUID.randomUUID();
    creneauReservePour(matchId);
    envoyer(matchAnnule(UUID.randomUUID(), 1, matchId));

    attendre(() -> "RELEASED".equals(statutReservation(matchId)));
    assertThat(statutReservation(matchId)).isEqualTo("RELEASED");
    assertThat(
            jdbc.sql("SELECT count(*) FROM outbox_event WHERE event_type = 'slot.released'")
                .query(Long.class)
                .single())
        .isEqualTo(1L);
  }

  @Test
  void le_creneau_libere_peut_etre_reserve_a_nouveau() throws Exception {
    UUID matchId = UUID.randomUUID();
    UUID creneau = creneauReservePour(matchId);
    envoyer(matchAnnule(UUID.randomUUID(), 1, matchId));
    attendre(() -> "RELEASED".equals(statutReservation(matchId)));

    // L'index unique ne porte que sur RESERVED : une nouvelle réservation du même créneau passe.
    reserver(creneau, UUID.randomUUID());
    assertThat(
            jdbc.sql("SELECT count(*) FROM slot_reservation WHERE slot_id = :s")
                .param("s", creneau)
                .query(Long.class)
                .single())
        .isEqualTo(2L);
  }

  /** Nouveau créneau d'une heure, réservé (RESERVED) pour ce match. @return slotId */
  private UUID creneauReservePour(UUID matchId) {
    UUID creneau = UUID.randomUUID();
    prochainCreneau++;
    jdbc.sql(
            """
            INSERT INTO slot (id, venue_id, start_at, end_at)
            VALUES (:id, :venue, TIMESTAMPTZ '2026-11-01 17:00+00' + make_interval(hours => :h),
                    TIMESTAMPTZ '2026-11-01 18:00+00' + make_interval(hours => :h))
            """)
        .param("id", creneau)
        .param("venue", VENUE)
        .param("h", prochainCreneau)
        .update();
    reserver(creneau, matchId);
    return creneau;
  }

  private void reserver(UUID creneau, UUID matchId) {
    jdbc.sql(
            """
            INSERT INTO slot_reservation (id, slot_id, match_id, status, total_amount_cents, currency)
            VALUES (:id, :slot, :match, 'RESERVED', 15000, 'MAD')
            """)
        .param("id", UUID.randomUUID())
        .param("slot", creneau)
        .param("match", matchId)
        .update();
  }

  /** match.cancelled (docs/events.md : matchId, reason, cancelledBy). */
  private static String matchAnnule(UUID eventId, int version, UUID matchId) {
    return """
        {"eventId":"%s","type":"match.cancelled","version":%d,"occurredAt":"2026-10-16T19:30:00Z",
         "producer":"match-service","data":{"matchId":"%s","reason":"ORGANIZER_CANCELLED",
         "cancelledBy":"%s"}}
        """
        .formatted(eventId, version, matchId, UUID.randomUUID());
  }

  private void envoyer(String message) throws Exception {
    kafkaTemplate.send(TOPIC, "cle", message).get(10, TimeUnit.SECONDS);
  }

  private String statutReservation(UUID matchId) {
    return jdbc.sql("SELECT status FROM slot_reservation WHERE match_id = :m")
        .param("m", matchId)
        .query(String.class)
        .optional()
        .orElse(null);
  }

  private long liberations(UUID matchId) {
    return jdbc.sql(
            """
            SELECT count(*) FROM outbox_event
            WHERE event_type = 'slot.released' AND payload->'data'->>'matchId' = :m
            """)
        .param("m", matchId.toString())
        .query(Long.class)
        .single();
  }

  /** Attend qu'une condition soit vraie, 30 secondes au maximum. */
  private static void attendre(BooleanSupplier condition) throws InterruptedException {
    long fin = System.currentTimeMillis() + 30_000;
    while (!condition.getAsBoolean() && System.currentTimeMillis() < fin) {
      Thread.sleep(200);
    }
  }
}
