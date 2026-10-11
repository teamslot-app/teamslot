package ma.teamslot.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** SCRUM-93 : réservation d'un créneau, sur un vrai PostgreSQL (Testcontainers, profil test). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class ReservationApiTests {

  private static final String VENUE = "a1000000-0000-4000-8000-000000000001";
  private static final String OTHER_VENUE = "a1000000-0000-4000-8000-000000000002";
  private static final String CHASUBLES = "c1000000-0000-4000-8000-000000000001";
  private static final String OTHER_EQUIPMENT = "c1000000-0000-4000-8000-000000000009";
  private static final String FREE_SLOT = slotId(1);
  private static final String RELEASED_SLOT = slotId(2);
  private static final String CONCURRENT_SLOT = slotId(3);
  private static final String PAST_SLOT = slotId(9);
  private static final String URL = "/api/v1/slot-reservations";
  private static final String PROBLEMS = "https://teamslot.app/problems/";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private static String slotId(int n) {
    return String.format("d1000000-0000-4000-8000-%012d", n);
  }

  @BeforeEach
  void prepareData() {
    jdbc.update("DELETE FROM slot_reservation_equipment");
    jdbc.update("DELETE FROM slot_reservation");
    jdbc.update("DELETE FROM slot");
    jdbc.update("DELETE FROM equipment");
    jdbc.update("DELETE FROM venue");
    jdbc.update("DELETE FROM outbox_event");

    insertVenue(VENUE);
    insertVenue(OTHER_VENUE);
    insertEquipment(CHASUBLES, VENUE, "Chasubles", 2000);
    insertEquipment(OTHER_EQUIPMENT, OTHER_VENUE, "Plots", 500);

    insertSlot(FREE_SLOT, "2026-10-12 17:00+00", "2026-10-12 18:00+00");
    insertSlot(RELEASED_SLOT, "2026-10-12 18:00+00", "2026-10-12 19:00+00");
    insertSlot(CONCURRENT_SLOT, "2026-10-12 19:00+00", "2026-10-12 20:00+00");
    // la date « maintenant » des tests est 2026-10-01 12:00Z : ce créneau est déjà passé
    insertSlot(PAST_SLOT, "2026-09-30 17:00+00", "2026-09-30 18:00+00");
  }

  private void insertVenue(String id) {
    jdbc.update(
        """
        INSERT INTO venue (id, owner_id, name, latitude, longitude, price_per_hour_cents, currency, accepts_on_site_payment)
        VALUES (?::uuid, ?::uuid, 'Terrain test', 31.63, -8.01, 15000, 'MAD', true)
        """,
        id, UUID.randomUUID().toString());
  }

  private void insertEquipment(String id, String venueId, String name, int priceCents) {
    jdbc.update(
        "INSERT INTO equipment (id, venue_id, name, price_cents) VALUES (?::uuid, ?::uuid, ?, ?)",
        id, venueId, name, priceCents);
  }

  private void insertSlot(String id, String start, String end) {
    jdbc.update(
        "INSERT INTO slot (id, venue_id, start_at, end_at) VALUES (?::uuid, ?::uuid, ?::timestamptz, ?::timestamptz)",
        id, VENUE, start, end);
  }

  private static String body(String slot, String match, String extra) {
    return "{\"slotId\":\"" + slot + "\",\"matchId\":\"" + match + "\"" + extra + "}";
  }

  private long count(String sql) {
    Long n = jdbc.queryForObject(sql, Long.class);
    return n == null ? 0 : n;
  }

  @Test
  void freeSlotIsReservedAtTheServerPrice() throws Exception {
    String match = UUID.randomUUID().toString();

    mvc.perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(FREE_SLOT, match, ",\"equipmentIds\":[\"" + CHASUBLES + "\"]")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.slotId").value(FREE_SLOT))
        .andExpect(jsonPath("$.matchId").value(match))
        .andExpect(jsonPath("$.venueId").value(VENUE))
        .andExpect(jsonPath("$.status").value("RESERVED"))
        .andExpect(jsonPath("$.totalPrice.amount").value(17000))
        .andExpect(jsonPath("$.totalPrice.currency").value("MAD"))
        .andExpect(jsonPath("$.acceptsOnSitePayment").value(true))
        .andExpect(jsonPath("$.startsAt").value("2026-10-12T17:00:00Z"))
        .andExpect(jsonPath("$.endsAt").value("2026-10-12T18:00:00Z"))
        .andExpect(jsonPath("$.equipment.length()").value(1))
        .andExpect(jsonPath("$.equipment[0].extraPrice.amount").value(2000));

    assertThat(count("SELECT count(*) FROM slot_reservation WHERE status = 'RESERVED'")).isEqualTo(1);
    assertThat(count("SELECT count(*) FROM slot_reservation_equipment")).isEqualTo(1);

    Map<String, Object> event =
        jdbc.queryForMap(
            """
            SELECT aggregate_id, event_type,
                   payload->'data'->>'matchId' AS match_id,
                   payload->'data'->>'totalAmount' AS total
            FROM outbox_event
            """);
    assertThat(event)
        .containsEntry("event_type", "slot.reserved")
        .containsEntry("aggregate_id", UUID.fromString(VENUE))
        .containsEntry("match_id", match)
        .containsEntry("total", "17000");
  }

  @Test
  void priceSentByTheClientIsIgnored() throws Exception {
    mvc.perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        FREE_SLOT,
                        UUID.randomUUID().toString(),
                        ",\"price\":1,\"totalPrice\":{\"amount\":1,\"currency\":\"MAD\"}")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.totalPrice.amount").value(15000));
  }

  @Test
  void secondReservationOfTheSameSlotIsAConflict() throws Exception {
    mvc.perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(FREE_SLOT, UUID.randomUUID().toString(), "")))
        .andExpect(status().isCreated());

    mvc.perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(FREE_SLOT, UUID.randomUUID().toString(), "")))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(PROBLEMS + "slot-already-reserved"));

    assertThat(count("SELECT count(*) FROM slot_reservation")).isEqualTo(1);
    assertThat(count("SELECT count(*) FROM outbox_event")).isEqualTo(1);
  }

  @Test
  void releasedSlotCanBeReservedAgain() throws Exception {
    jdbc.update(
        """
        INSERT INTO slot_reservation (id, slot_id, match_id, status, total_amount_cents, currency)
        VALUES (?::uuid, ?::uuid, ?::uuid, 'RELEASED', 15000, 'MAD')
        """,
        UUID.randomUUID().toString(), RELEASED_SLOT, UUID.randomUUID().toString());

    mvc.perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(RELEASED_SLOT, UUID.randomUUID().toString(), "")))
        .andExpect(status().isCreated());

    assertThat(count("SELECT count(*) FROM slot_reservation WHERE status = 'RESERVED'")).isEqualTo(1);
    assertThat(count("SELECT count(*) FROM slot_reservation WHERE status = 'RELEASED'")).isEqualTo(1);
  }

  @Test
  void concurrentReservationsOfTheSameSlotGiveExactlyOneCreated() throws Exception {
    int attempts = 6;
    ExecutorService pool = Executors.newFixedThreadPool(attempts);
    CountDownLatch ready = new CountDownLatch(attempts);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Integer>> results = new ArrayList<>();

    for (int i = 0; i < attempts; i++) {
      String payload = body(CONCURRENT_SLOT, UUID.randomUUID().toString(), "");
      results.add(
          pool.submit(
              () -> {
                ready.countDown();
                start.await();
                return mvc.perform(
                        post(URL).contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              }));
    }
    ready.await();
    start.countDown();

    int created = 0;
    int conflicts = 0;
    for (Future<Integer> result : results) {
      int code = result.get(30, TimeUnit.SECONDS);
      if (code == 201) {
        created++;
      } else if (code == 409) {
        conflicts++;
      }
    }
    pool.shutdown();

    assertThat(created).isEqualTo(1);
    assertThat(conflicts).isEqualTo(attempts - 1);
    assertThat(count("SELECT count(*) FROM slot_reservation WHERE status = 'RESERVED'")).isEqualTo(1);
    assertThat(count("SELECT count(*) FROM outbox_event")).isEqualTo(1);
  }

  @Test
  void slotAlreadyStartedIsRefused() throws Exception {
    mvc.perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(PAST_SLOT, UUID.randomUUID().toString(), "")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(PROBLEMS + "slot-already-started"));

    assertThat(count("SELECT count(*) FROM slot_reservation")).isZero();
    assertThat(count("SELECT count(*) FROM outbox_event")).isZero();
  }

  @Test
  void slotAlreadyStartedIsNotAvailableInTheList() throws Exception {
    mvc.perform(get("/api/v1/venues/" + VENUE + "/slots").param("date", "2026-09-30"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].available").value(false));
  }

  @Test
  void unknownSlotIsNotFound() throws Exception {
    mvc.perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "")))
        .andExpect(status().isNotFound());
  }

  @Test
  void equipmentOfAnotherVenueIsNotFoundAndNothingIsWritten() throws Exception {
    mvc.perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        FREE_SLOT,
                        UUID.randomUUID().toString(),
                        ",\"equipmentIds\":[\"" + OTHER_EQUIPMENT + "\"]")))
        .andExpect(status().isNotFound());

    assertThat(count("SELECT count(*) FROM slot_reservation")).isZero();
  }

  @Test
  void missingMatchIdIsABadRequest() throws Exception {
    mvc.perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slotId\":\"" + FREE_SLOT + "\"}"))
        .andExpect(status().isBadRequest());
  }
}
