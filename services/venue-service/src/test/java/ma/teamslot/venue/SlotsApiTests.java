package ma.teamslot.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** SCRUM-58 : créneaux d'un terrain et prix, sur un vrai PostgreSQL (Testcontainers, profil test). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SlotsApiTests {

  private static final String VENUE = "a1000000-0000-4000-8000-000000000001";
  private static final String OTHER_VENUE = "a1000000-0000-4000-8000-000000000002";
  private static final String CHASUBLES = "c1000000-0000-4000-8000-000000000001";
  private static final String BALLON = "c1000000-0000-4000-8000-000000000002";
  private static final String OTHER_EQUIPMENT = "c1000000-0000-4000-8000-000000000009";
  private static final String FIRST_SLOT = slotId(1);
  private static final String SLOTS = "/api/v1/venues/" + VENUE + "/slots";
  private static final String PRICE = "/api/v1/venues/" + VENUE + "/price";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private static String slotId(int n) {
    return String.format("d1000000-0000-4000-8000-%012d", n);
  }

  private static int count(String body, String needle) {
    return body.split(Pattern.quote(needle), -1).length - 1;
  }

  @BeforeEach
  void prepareData() {
    jdbc.update("DELETE FROM slot_reservation_equipment");
    jdbc.update("DELETE FROM slot_reservation");
    jdbc.update("DELETE FROM slot");
    jdbc.update("DELETE FROM equipment");
    jdbc.update("DELETE FROM venue");

    insertVenue(VENUE, "Terrain test");
    insertVenue(OTHER_VENUE, "Autre terrain");
    jdbc.update(
        "INSERT INTO equipment (id, venue_id, name, price_cents) VALUES (?::uuid, ?::uuid, 'Chasubles', 2000)",
        CHASUBLES, VENUE);
    jdbc.update(
        "INSERT INTO equipment (id, venue_id, name, price_cents) VALUES (?::uuid, ?::uuid, 'Ballon', 1000)",
        BALLON, VENUE);
    jdbc.update(
        "INSERT INTO equipment (id, venue_id, name, price_cents) VALUES (?::uuid, ?::uuid, 'Plots', 500)",
        OTHER_EQUIPMENT, OTHER_VENUE);

    // 5 créneaux d'1 h le 12/10/2026 : 17:00Z à 22:00Z = 18 h à 23 h à Marrakech (UTC+1)
    for (int hour = 17; hour <= 21; hour++) {
      insertSlot(slotId(hour - 16), VENUE, "2026-10-12 " + hour + ":00+00", "2026-10-12 " + (hour + 1) + ":00+00");
    }
    // 1er créneau : réservation active. 2e créneau : réservation libérée, donc toujours disponible
    insertReservation("e1000000-0000-4000-8000-000000000001", slotId(1), "ACTIVE");
    insertReservation("e1000000-0000-4000-8000-000000000002", slotId(2), "RELEASED");
  }

  private void insertVenue(String id, String name) {
    jdbc.update(
        """
        INSERT INTO venue (id, owner_id, name, latitude, longitude, price_per_hour_cents, currency, accepts_on_site_payment)
        VALUES (?::uuid, ?::uuid, ?, 31.63, -8.01, 15000, 'MAD', true)
        """,
        id, UUID.randomUUID().toString(), name);
  }

  private void insertSlot(String id, String venueId, String start, String end) {
    jdbc.update(
        "INSERT INTO slot (id, venue_id, start_at, end_at) VALUES (?::uuid, ?::uuid, ?::timestamptz, ?::timestamptz)",
        id, venueId, start, end);
  }

  private void insertReservation(String id, String slotId, String status) {
    jdbc.update(
        """
        INSERT INTO slot_reservation (id, slot_id, match_id, status, total_amount_cents, currency)
        VALUES (?::uuid, ?::uuid, ?::uuid, ?, 15000, 'MAD')
        """,
        id, slotId, UUID.randomUUID().toString(), status);
  }

  @Test
  void fiveSlotsOfWhichOneIsTaken() throws Exception {
    String body =
        mvc.perform(get(SLOTS).param("date", "2026-10-12"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(5))
            .andExpect(jsonPath("$[0].available").value(false))
            .andExpect(jsonPath("$[1].available").value(true))
            .andExpect(jsonPath("$[0].start").value("2026-10-12T17:00:00Z"))
            .andExpect(jsonPath("$[0].price.amount").value(15000))
            .andExpect(jsonPath("$[0].price.currency").value("MAD"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(count(body, "\"available\":false")).isEqualTo(1);
    assertThat(count(body, "\"available\":true")).isEqualTo(4);
  }

  @Test
  void lateSlotBelongsToTheLocalDay() throws Exception {
    // 22:00Z = 23 h le 14 à Marrakech ; 23:00Z = 00 h le 15
    insertSlot(slotId(10), VENUE, "2026-10-14 22:00+00", "2026-10-14 23:00+00");
    insertSlot(slotId(11), VENUE, "2026-10-14 23:00+00", "2026-10-15 00:00+00");

    mvc.perform(get(SLOTS).param("date", "2026-10-14"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(slotId(10)));

    mvc.perform(get(SLOTS).param("date", "2026-10-15"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(slotId(11)));
  }

  @Test
  void dayWithoutSlotsReturnsAnEmptyList() throws Exception {
    mvc.perform(get(SLOTS).param("date", "2026-11-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void priceWithEquipmentAddsTheSupplement() throws Exception {
    mvc.perform(get(PRICE).param("slot", FIRST_SLOT).param("equipment", CHASUBLES))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slotId").value(FIRST_SLOT))
        .andExpect(jsonPath("$.base.amount").value(15000))
        .andExpect(jsonPath("$.equipmentTotal.amount").value(2000))
        .andExpect(jsonPath("$.total.amount").value(17000))
        .andExpect(jsonPath("$.total.currency").value("MAD"));
  }

  @Test
  void priceWithTwoEquipmentsAndWithNone() throws Exception {
    mvc.perform(get(PRICE).param("slot", FIRST_SLOT).param("equipment", CHASUBLES + "," + BALLON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.equipmentTotal.amount").value(3000))
        .andExpect(jsonPath("$.total.amount").value(18000));

    mvc.perform(get(PRICE).param("slot", FIRST_SLOT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.equipmentTotal.amount").value(0))
        .andExpect(jsonPath("$.total.amount").value(15000));
  }

  @Test
  void priceSentByTheClientIsIgnored() throws Exception {
    mvc.perform(
            get(PRICE).param("slot", FIRST_SLOT).param("equipment", CHASUBLES).param("price", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total.amount").value(17000));
  }

  @Test
  void invalidDateIsABadRequestInProblemDetails() throws Exception {
    mvc.perform(get(SLOTS).param("date", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void missingDateIsABadRequest() throws Exception {
    mvc.perform(get(SLOTS)).andExpect(status().isBadRequest());
  }

  @Test
  void unknownVenueIsNotFound() throws Exception {
    mvc.perform(
            get("/api/v1/venues/a1000000-0000-4000-8000-0000000000ff/slots")
                .param("date", "2026-10-12"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.type").value("https://teamslot.app/problems/not-found"));
  }

  @Test
  void unknownSlotIsNotFound() throws Exception {
    mvc.perform(get(PRICE).param("slot", UUID.randomUUID().toString()))
        .andExpect(status().isNotFound());
  }

  @Test
  void equipmentOfAnotherVenueIsNotFound() throws Exception {
    mvc.perform(get(PRICE).param("slot", FIRST_SLOT).param("equipment", OTHER_EQUIPMENT))
        .andExpect(status().isNotFound());
  }
}
