package ma.teamslot.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.teamslot.match.application.CallerNotAuthenticatedException;
import ma.teamslot.match.application.SlotAlreadyReservedException;
import ma.teamslot.match.application.SlotNotFoundException;
import ma.teamslot.match.application.VenueClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** POST /api/v1/matches, avec un faux venue-service (règle d'équipe : simuler plutôt qu'attendre). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MatchCreationTests {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired FakeVenueClient venue;

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        FakeVenueClient fakeVenueClient() {
            return new FakeVenueClient();
        }
    }

    static class FakeVenueClient implements VenueClient {
        VenueReservation reservation;
        RuntimeException erreur;
        String dernierJeton;

        void reinitialiser() {
            reservation = new VenueReservation(UUID.randomUUID(),
                    Instant.parse("2026-10-20T18:00:00Z"), 30000L, "MAD", true);
            erreur = null;
            dernierJeton = null;
        }

        @Override
        public VenueReservation reserve(UUID matchId, UUID slotId, List<UUID> equipmentIds, String bearerToken) {
            dernierJeton = bearerToken;
            if (erreur != null) {
                throw erreur;
            }
            return reservation;
        }
    }

    /** Jeton factice (non signé) dont la claim sub est l'identifiant donné. */
    static String jeton(UUID sub) {
        Base64.Encoder e = Base64.getUrlEncoder().withoutPadding();
        String h = e.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String p = e.encodeToString(("{\"sub\":\"" + sub + "\"}").getBytes(StandardCharsets.UTF_8));
        return h + "." + p + ".sig";
    }

    @BeforeEach
    void preparer() {
        venue.reinitialiser();
        jdbc.sql("DELETE FROM outbox_event").update();
        jdbc.sql("DELETE FROM matches").update();
    }

    @Test
    void creneau_libre_cree_le_match_et_ecrit_match_created() throws Exception {
        UUID caller = UUID.randomUUID();
        String jeton = jeton(caller);

        mvc.perform(post("/api/v1/matches").header("Authorization", "Bearer " + jeton)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.kind").value("FRIENDLY"))
                .andExpect(jsonPath("$.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.ownerId").value(caller.toString()))
                .andExpect(jsonPath("$.payerId").value(caller.toString()))
                .andExpect(jsonPath("$.totalPrice.amount").value(30000))
                .andExpect(jsonPath("$.totalPrice.currency").value("MAD"));

        assertThat(venue.dernierJeton).isEqualTo(jeton);

        Map<String, Object> match = jdbc.sql("SELECT id, owner_id, payer_id FROM matches")
                .query().singleRow();
        assertThat(match).containsEntry("owner_id", caller);
        assertThat(match).containsEntry("payer_id", caller);

        Map<String, Object> evenement = jdbc.sql("""
                SELECT aggregate_id, event_type,
                       payload->'data'->>'ownerId' AS owner_id,
                       payload->'data'->>'totalAmount' AS total,
                       payload->'data'->>'acceptsOnSitePayment' AS sur_place
                FROM outbox_event
                """).query().singleRow();
        assertThat(evenement).containsEntry("event_type", "match.created");
        assertThat(evenement).containsEntry("aggregate_id", match.get("id"));
        assertThat(evenement).containsEntry("owner_id", caller.toString());
        assertThat(evenement).containsEntry("total", "30000");
        assertThat(evenement).containsEntry("sur_place", "true");
    }

    @Test
    void le_prix_envoye_par_le_client_est_ignore() throws Exception {
        mvc.perform(post("/api/v1/matches").header("Authorization", "Bearer " + jeton(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":\"" + UUID.randomUUID()
                                + "\",\"price\":1,\"totalPrice\":{\"amount\":1,\"currency\":\"MAD\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalPrice.amount").value(30000));
    }

    @Test
    void sans_jeton_401() throws Exception {
        mvc.perform(post("/api/v1/matches").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(nombre("matches")).isZero();
    }

    @Test
    void l_en_tete_x_user_id_ne_suffit_pas() throws Exception {
        mvc.perform(post("/api/v1/matches").header("X-User-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(nombre("matches")).isZero();
    }

    @Test
    void jeton_illisible_401() throws Exception {
        mvc.perform(post("/api/v1/matches").header("Authorization", "Bearer abc.def.ghi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(nombre("matches")).isZero();
    }

    @Test
    void jeton_refuse_par_venue_401() throws Exception {
        venue.erreur = new CallerNotAuthenticatedException();

        mvc.perform(post("/api/v1/matches").header("Authorization", "Bearer " + jeton(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(nombre("matches")).isZero();
    }

    @Test
    void creneau_deja_pris_409_sans_match_ni_evenement() throws Exception {
        UUID slot = UUID.randomUUID();
        venue.erreur = new SlotAlreadyReservedException(slot);

        mvc.perform(post("/api/v1/matches").header("Authorization", "Bearer " + jeton(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":\"" + slot + "\"}"))
                .andExpect(status().isConflict());

        assertThat(nombre("matches")).isZero();
        assertThat(nombre("outbox_event")).isZero();
    }

    @Test
    void creneau_inexistant_404() throws Exception {
        UUID slot = UUID.randomUUID();
        venue.erreur = new SlotNotFoundException(slot);

        mvc.perform(post("/api/v1/matches").header("Authorization", "Bearer " + jeton(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":\"" + slot + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void slotId_absent_400() throws Exception {
        mvc.perform(post("/api/v1/matches").header("Authorization", "Bearer " + jeton(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void echec_apres_reservation_500_sans_match_ni_evenement() throws Exception {
        // un montant négatif viole la contrainte CHECK : l'enregistrement échoue après la réservation
        venue.reservation = new VenueClient.VenueReservation(UUID.randomUUID(),
                Instant.parse("2026-10-20T18:00:00Z"), -1L, "MAD", false);

        mvc.perform(post("/api/v1/matches").header("Authorization", "Bearer " + jeton(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isInternalServerError());

        assertThat(nombre("matches")).isZero();
        assertThat(nombre("outbox_event")).isZero();
    }

    private long nombre(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }
}
