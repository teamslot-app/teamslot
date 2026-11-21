package ma.teamslot.venue;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** SCRUM-138 : liste des terrains (position facultative, pagination bornée, tri stable). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ListeTerrainsTests {

  private static final String VENUES = "/api/v1/venues";

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbc;

  @Test
  void sans_position_200_tries_par_nom() throws Exception {
    String s = suffixe();
    creerTerrain("Aaa-2-" + s, 50.0, 10.0);
    creerTerrain("Aaa-1-" + s, 50.0, 10.0);
    String corps =
        mockMvc.perform(get(VENUES).param("size", "50").with(joueur()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.size").value(50))
            .andReturn().getResponse().getContentAsString();
    assertTrue(corps.contains("Aaa-1-" + s));
    assertTrue(corps.indexOf("Aaa-1-" + s) < corps.indexOf("Aaa-2-" + s));
  }

  @Test
  void avec_position_tries_par_distance_rayon_applique() throws Exception {
    String s = suffixe();
    creerTerrain("Pos-C-" + s, 40.1, 10.0);
    creerTerrain("Pos-B-" + s, 40.001, 10.0);
    creerTerrain("Pos-A-" + s, 40.0, 10.0);
    mockMvc.perform(get(VENUES).param("lat", "40.0").param("lon", "10.0").param("radius", "1000").with(joueur()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].name").value("Pos-A-" + s))
        .andExpect(jsonPath("$.content[0].distanceMeters").value(0))
        .andExpect(jsonPath("$.content[1].name").value("Pos-B-" + s))
        .andExpect(jsonPath("$.content[1].distanceMeters").value(111));
  }

  @Test
  void equipements_presents() throws Exception {
    String s = suffixe();
    UUID venueId = creerTerrain("Equip-" + s, 45.0, 10.0);
    jdbc.update(
        "insert into equipment (id, venue_id, name, price_cents) values (?, ?, ?, ?)",
        UUID.randomUUID(), venueId, "Chasubles", 500);
    mockMvc.perform(get(VENUES).param("lat", "45.0").param("lon", "10.0").param("radius", "1000").with(joueur()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].equipment[0].name").value("Chasubles"))
        .andExpect(jsonPath("$.content[0].equipment[0].extraPrice.amount").value(500))
        .andExpect(jsonPath("$.content[0].equipment[0].extraPrice.currency").value("MAD"));
  }

  @Test
  void taille_par_defaut_20() throws Exception {
    mockMvc.perform(get(VENUES).with(joueur()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.page").value(0));
  }

  @Test
  void lat_seul_400() throws Exception {
    mockMvc.perform(get(VENUES).param("lat", "31.6").with(joueur())).andExpect(status().isBadRequest());
  }

  @Test
  void lon_seul_400() throws Exception {
    mockMvc.perform(get(VENUES).param("lon", "-8.0").with(joueur())).andExpect(status().isBadRequest());
  }

  @Test
  void size_superieur_a_50_400() throws Exception {
    mockMvc.perform(get(VENUES).param("size", "51").with(joueur())).andExpect(status().isBadRequest());
  }

  @Test
  void sans_jeton_401() throws Exception {
    mockMvc.perform(get(VENUES)).andExpect(status().isUnauthorized());
  }

  @Test
  void latitude_hors_limites_400() throws Exception {
    mockMvc.perform(get(VENUES).param("lat", "91").param("lon", "10").with(joueur()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rayon_zero_400() throws Exception {
    mockMvc.perform(get(VENUES).param("lat", "40.0").param("lon", "10.0").param("radius", "0").with(joueur()))
        .andExpect(status().isBadRequest());
    mockMvc.perform(get(VENUES).param("radius", "0").with(joueur())).andExpect(status().isBadRequest());
  }

  @Test
  void la_reponse_ne_contient_aucune_donnee_privee() throws Exception {
    creerTerrain("Prive-" + suffixe(), 46.0, 10.0);
    mockMvc.perform(get(VENUES).param("size", "50").with(joueur()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").exists())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
            .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ownerId"))));
  }

  @Test
  void page_negative_400() throws Exception {
    mockMvc.perform(get(VENUES).param("page", "-1").with(joueur()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void page_au_dela_du_total_200_liste_vide() throws Exception {
    mockMvc.perform(get(VENUES).param("page", "1000").with(joueur()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());
  }

  @Test
  void position_invalide_400() throws Exception {
    String[][] positions = {{"-91", "10"}, {"NaN", "10"}, {"40", "NaN"}, {"40", "-181"}, {"40", "181"}};
    for (String[] position : positions) {
      mockMvc.perform(get(VENUES).param("lat", position[0]).param("lon", position[1]).with(joueur()))
          .andExpect(status().isBadRequest());
    }
  }

  @Test
  void size_zero_400() throws Exception {
    mockMvc.perform(get(VENUES).param("size", "0").with(joueur()))
        .andExpect(status().isBadRequest());
  }

  private UUID creerTerrain(String nom, double lat, double lon) throws Exception {
    String corps =
        "{\"name\":\"" + nom + "\",\"latitude\":" + lat + ",\"longitude\":" + lon
            + ",\"pricePerHour\":{\"amount\":15000,\"currency\":\"MAD\"},\"acceptsOnSitePayment\":true}";
    mockMvc.perform(post(VENUES).with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("role", "VENUE_MANAGER")))
            .contentType(MediaType.APPLICATION_JSON).content(corps))
        .andExpect(status().isCreated());
    return jdbc.queryForObject("select id from venue where name = ?", UUID.class, nom);
  }

  private static String suffixe() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private static RequestPostProcessor joueur() {
    return jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("role", "PLAYER"));
  }
}
