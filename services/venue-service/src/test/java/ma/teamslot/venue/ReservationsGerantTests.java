package ma.teamslot.venue;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** SCRUM-139 : le gérant voit les réservations de son terrain, et seulement les siennes. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReservationsGerantTests {

  private static final String VENUES = "/api/v1/venues";
  private static final Instant FUTUR = Instant.parse("2099-01-01T10:00:00Z");
  private static final String JOUR = "2099-01-01";

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbc;

  @Test
  void gerant_du_terrain_200_avec_la_reservation_et_sans_telephone() throws Exception {
    UUID gerant = UUID.randomUUID();
    UUID matchId = UUID.randomUUID();
    UUID venueId = terrainAvecReservation(gerant, matchId);
    mockMvc.perform(get(VENUES + "/" + venueId + "/reservations").param("date", JOUR).with(jeton(gerant, "VENUE_MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].matchId").value(matchId.toString()))
        .andExpect(jsonPath("$[0].status").value("RESERVED"))
        .andExpect(jsonPath("$[0].phone").doesNotExist());
  }

  @Test
  void gerant_dun_autre_terrain_403_et_n_apprend_rien() throws Exception {
    UUID matchId = UUID.randomUUID();
    UUID venueId = terrainAvecReservation(UUID.randomUUID(), matchId);
    mockMvc.perform(get(VENUES + "/" + venueId + "/reservations").param("date", JOUR).with(jeton(UUID.randomUUID(), "VENUE_MANAGER")))
        .andExpect(status().isForbidden())
        .andExpect(content().string(not(containsString(matchId.toString()))));
  }

  @Test
  void joueur_403() throws Exception {
    UUID gerant = UUID.randomUUID();
    UUID venueId = terrainAvecReservation(gerant, UUID.randomUUID());
    mockMvc.perform(get(VENUES + "/" + venueId + "/reservations").param("date", JOUR).with(jeton(UUID.randomUUID(), "PLAYER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void sans_jeton_401() throws Exception {
    mockMvc.perform(get(VENUES + "/" + UUID.randomUUID() + "/reservations").param("date", JOUR))
        .andExpect(status().isUnauthorized());
  }

  /** Crée un terrain (via l'API), un créneau (via l'API) et une réservation (en base), pour ce gérant. */
  private UUID terrainAvecReservation(UUID gerant, UUID matchId) throws Exception {
    mockMvc.perform(post(VENUES).with(jeton(gerant, "VENUE_MANAGER")).contentType(MediaType.APPLICATION_JSON).content(terrain()))
        .andExpect(status().isCreated());
    UUID venueId = jdbc.queryForObject("select id from venue where owner_id = ?", UUID.class, gerant);
    mockMvc.perform(post(VENUES + "/" + venueId + "/slots").with(jeton(gerant, "VENUE_MANAGER"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"starts\":[\"" + FUTUR + "\"]}"))
        .andExpect(status().isCreated());
    UUID slotId = jdbc.queryForObject("select id from slot where venue_id = ?", UUID.class, venueId);
    jdbc.update(
        "insert into slot_reservation (id, slot_id, match_id, status, total_amount_cents, currency)"
            + " values (?, ?, ?, 'RESERVED', 15000, 'MAD')",
        UUID.randomUUID(), slotId, matchId);
    return venueId;
  }

  private static RequestPostProcessor jeton(UUID sub, String role) {
    return jwt().jwt(j -> j.subject(sub.toString()).claim("role", role));
  }

  private static String terrain() {
    return "{\"name\":\"Terrain\",\"latitude\":31.63,\"longitude\":-8.0,"
        + "\"pricePerHour\":{\"amount\":15000,\"currency\":\"MAD\"},\"acceptsOnSitePayment\":true}";
  }
}
