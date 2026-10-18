package ma.teamslot.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

/** SCRUM-74 : le gérant publie un terrain et ses créneaux. Rôle et propriétaire viennent du JWT vérifié. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicationTerrainTests {

  private static final String VENUES = "/api/v1/venues";
  private static final Instant FUTUR = Instant.parse("2099-01-01T10:00:00Z");
  private static final Instant PASSE = Instant.parse("2000-01-01T10:00:00Z");

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbc;

  @Test
  void joueur_403_et_aucun_terrain_cree() throws Exception {
    UUID joueur = UUID.randomUUID();
    mockMvc.perform(post(VENUES).with(jeton(joueur, "PLAYER")).contentType(MediaType.APPLICATION_JSON).content(terrain("Terrain A")))
        .andExpect(status().isForbidden());
    assertThat(jdbc.queryForObject("select count(*) from venue where owner_id = ?", Long.class, joueur)).isZero();
  }

  @Test
  void gerant_dun_autre_terrain_403() throws Exception {
    UUID proprietaire = UUID.randomUUID();
    UUID venueId = creerTerrain(proprietaire);
    UUID intrus = UUID.randomUUID();
    mockMvc.perform(post(VENUES + "/" + venueId + "/slots").with(jeton(intrus, "VENUE_MANAGER"))
            .contentType(MediaType.APPLICATION_JSON).content(creneaux(FUTUR)))
        .andExpect(status().isForbidden());
  }

  @Test
  void entree_invalide_400() throws Exception {
    UUID gerant = UUID.randomUUID();
    String latitudeHorsBornes = terrain("Terrain B").replace("\"latitude\":31.63", "\"latitude\":200");
    mockMvc.perform(post(VENUES).with(jeton(gerant, "VENUE_MANAGER")).contentType(MediaType.APPLICATION_JSON).content(latitudeHorsBornes))
        .andExpect(status().isBadRequest());
    mockMvc.perform(post(VENUES).with(jeton(gerant, "VENUE_MANAGER")).contentType(MediaType.APPLICATION_JSON).content(terrain("   ")))
        .andExpect(status().isBadRequest());

    UUID venueId = creerTerrain(gerant);
    mockMvc.perform(post(VENUES + "/" + venueId + "/slots").with(jeton(gerant, "VENUE_MANAGER"))
            .contentType(MediaType.APPLICATION_JSON).content(creneaux(PASSE)))
        .andExpect(status().isBadRequest());
    mockMvc.perform(post(VENUES + "/" + venueId + "/slots").with(jeton(gerant, "VENUE_MANAGER"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"starts\":[]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void creneau_en_double_ou_chevauchant_409() throws Exception {
    UUID gerant = UUID.randomUUID();
    UUID venueId = creerTerrain(gerant);
    mockMvc.perform(post(VENUES + "/" + venueId + "/slots").with(jeton(gerant, "VENUE_MANAGER"))
            .contentType(MediaType.APPLICATION_JSON).content(creneaux(FUTUR)))
        .andExpect(status().isCreated());
    mockMvc.perform(post(VENUES + "/" + venueId + "/slots").with(jeton(gerant, "VENUE_MANAGER"))
            .contentType(MediaType.APPLICATION_JSON).content(creneaux(FUTUR)))
        .andExpect(status().isConflict());
    mockMvc.perform(post(VENUES + "/" + venueId + "/slots").with(jeton(gerant, "VENUE_MANAGER"))
            .contentType(MediaType.APPLICATION_JSON).content(creneaux(FUTUR.plusSeconds(1800))))
        .andExpect(status().isConflict());
  }

  @Test
  void gerant_publie_un_terrain_et_ses_creneaux_201() throws Exception {
    UUID gerant = UUID.randomUUID();
    mockMvc.perform(post(VENUES).with(jeton(gerant, "VENUE_MANAGER")).contentType(MediaType.APPLICATION_JSON)
            .content(terrain("Terrain C").replace("\"acceptsOnSitePayment\":true",
                "\"acceptsOnSitePayment\":true,\"equipment\":[{\"name\":\"Chasubles\",\"extraPrice\":{\"amount\":2000,\"currency\":\"MAD\"}}]")))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.name").value("Terrain C"));

    UUID venueId = jdbc.queryForObject("select id from venue where owner_id = ?", UUID.class, gerant);
    assertThat(jdbc.queryForObject("select count(*) from equipment where venue_id = ?", Long.class, venueId)).isEqualTo(1L);

    mockMvc.perform(post(VENUES + "/" + venueId + "/slots").with(jeton(gerant, "VENUE_MANAGER"))
            .contentType(MediaType.APPLICATION_JSON).content(creneaux(FUTUR, FUTUR.plusSeconds(3600))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.length()").value(2));
  }

  private UUID creerTerrain(UUID gerant) throws Exception {
    mockMvc.perform(post(VENUES).with(jeton(gerant, "VENUE_MANAGER")).contentType(MediaType.APPLICATION_JSON).content(terrain("Terrain")))
        .andExpect(status().isCreated());
    return jdbc.queryForObject("select id from venue where owner_id = ?", UUID.class, gerant);
  }

  private static RequestPostProcessor jeton(UUID sub, String role) {
    return jwt().jwt(j -> j.subject(sub.toString()).claim("role", role));
  }

  private static String terrain(String nom) {
    return "{\"name\":\"" + nom + "\",\"latitude\":31.63,\"longitude\":-8.0,"
        + "\"pricePerHour\":{\"amount\":15000,\"currency\":\"MAD\"},\"acceptsOnSitePayment\":true}";
  }

  private static String creneaux(Instant... debuts) {
    StringBuilder json = new StringBuilder("{\"starts\":[");
    for (int i = 0; i < debuts.length; i++) {
      json.append(i == 0 ? "" : ",").append('"').append(debuts[i]).append('"');
    }
    return json.append("]}").toString();
  }
}
