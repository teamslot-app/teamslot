package ma.teamslot.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** SCRUM-74 : venue.created est écrit dans l'outbox, dans la transaction de la création. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VenueCreatedOutboxTests {

  private static final String CORPS =
      "{\"name\":\"Terrain Outbox\",\"latitude\":31.63,\"longitude\":-8.0,"
          + "\"pricePerHour\":{\"amount\":15000,\"currency\":\"MAD\"},\"acceptsOnSitePayment\":true}";

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbc;

  @Test
  void creation_ecrit_venue_created_dans_loutbox() throws Exception {
    UUID gerant = UUID.randomUUID();
    mockMvc.perform(post("/api/v1/venues")
            .with(jwt().jwt(j -> j.subject(gerant.toString()).claim("role", "VENUE_MANAGER")))
            .contentType(MediaType.APPLICATION_JSON).content(CORPS))
        .andExpect(status().isCreated());

    UUID venueId = jdbc.queryForObject("select id from venue where owner_id = ?", UUID.class, gerant);
    assertThat(jdbc.queryForObject(
            "select count(*) from outbox_event where aggregate_id = ? and event_type = 'venue.created'",
            Long.class, venueId)).isEqualTo(1L);
    String payload = jdbc.queryForObject(
        "select payload::text from outbox_event where aggregate_id = ? and event_type = 'venue.created'",
        String.class, venueId);
    assertThat(payload).contains(gerant.toString()).contains("Terrain Outbox");
  }

  @Test
  void refus_403_nepublie_aucun_evenement() throws Exception {
    UUID joueur = UUID.randomUUID();
    long avant = jdbc.queryForObject("select count(*) from outbox_event where event_type = 'venue.created'", Long.class);
    mockMvc.perform(post("/api/v1/venues")
            .with(jwt().jwt(j -> j.subject(joueur.toString()).claim("role", "PLAYER")))
            .contentType(MediaType.APPLICATION_JSON).content(CORPS))
        .andExpect(status().isForbidden());
    assertThat(jdbc.queryForObject("select count(*) from outbox_event where event_type = 'venue.created'", Long.class))
        .isEqualTo(avant);
  }
}
