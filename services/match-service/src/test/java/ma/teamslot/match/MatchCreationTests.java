package ma.teamslot.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    private static final String HEADER = "X-User-Id";

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

        void reinitialiser() {
            reservation = new VenueReservation(UUID.randomUUID(),
                    Instant.parse("2026-10-20T18:00:00Z"), 30000L, "MAD", true);
            erreur = null;
        }

        @Override
        public VenueReservation reserve(UUID matchId, UUID slotId, List<UUID> equipmentIds) {
            if (erreur != null) {
                throw erreur;
            }
            return reservation;
        }
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

        mvc.perform(post("/api/v1/matches").header(HEADER, caller.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.kind").value("FRIENDLY"))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.ownerId").value(caller.toString()))
                .andExpect(jsonPath("$.payerId").value(caller.toString()))
                .andExpect(jsonPath("$.totalPrice.amount").value(30000))
                .andExpect(jsonPath("$.totalPrice.currency").value("MAD"));

        Map<String, Object> match = jdbc.sql("SELECT id, owner_id, payer_id FROM matches")
                .query().singleRow();
        assertThat(match.get("owner_id")).isEqualTo(caller);
        assertThat(match.get("payer_id")).isEqualTo(caller);

        Map<String, Object> evenement = jdbc.sql("""
                SELECT aggregate_id, event_type,
                       payload->'data'->>'ownerId' AS owner_id,
                       payload->'data'->>'totalAmount' AS total,
                       payload->'data'->>'acceptsOnSitePayment' AS sur_place
                FROM outbox_event
                """).query().singleRow();
        assertThat(evenement.get("event_type")).isEqualTo("match.created");
        assertThat(evenement.get("aggregate_id")).isEqualTo(match.get("id"));
        assertThat(evenement.get("owner_id")).isEqualTo(caller.toString());
        assertThat(evenement.get("total")).isEqualTo("30000");
        assertThat(evenement.get("sur_place")).isEqualTo("true");
    }

    @Test
    void le_prix_envoye_par_le_client_est_ignore() throws Exception {
        mvc.perform(post("/api/v1/matches").header(HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":\"" + UUID.randomUUID()
                                + "\",\"price\":1,\"totalPrice\":{\"amount\":1,\"currency\":\"MAD\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalPrice.amount").value(30000));
    }

    @Test
    void sans_appelant_401() throws Exception {
        mvc.perform(post("/api/v1/matches").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(nombre("matches")).isZero();
    }

    @Test
    void creneau_deja_pris_409_sans_match_ni_evenement() throws Exception {
        UUID slot = UUID.randomUUID();
        venue.erreur = new SlotAlreadyReservedException(slot);

        mvc.perform(post("/api/v1/matches").header(HEADER, UUID.randomUUID().toString())
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

        mvc.perform(post("/api/v1/matches").header(HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":\"" + slot + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void slotId_absent_400() throws Exception {
        mvc.perform(post("/api/v1/matches").header(HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void echec_apres_reservation_ecrit_match_cancelled_pour_que_venue_libere() throws Exception {
        UUID caller = UUID.randomUUID();
        // un montant négatif viole la contrainte CHECK : l'enregistrement échoue après la réservation
        venue.reservation = new VenueClient.VenueReservation(UUID.randomUUID(),
                Instant.parse("2026-10-20T18:00:00Z"), -1L, "MAD", false);

        mvc.perform(post("/api/v1/matches").header(HEADER, caller.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isInternalServerError());

        assertThat(nombre("matches")).isZero();
        Map<String, Object> evenement = jdbc.sql("""
                SELECT event_type, payload->'data'->>'reason' AS reason,
                       payload->'data'->>'cancelledBy' AS cancelled_by
                FROM outbox_event
                """).query().singleRow();
        assertThat(evenement.get("event_type")).isEqualTo("match.cancelled");
        assertThat(evenement.get("reason")).isEqualTo("creation_failed");
        assertThat(evenement.get("cancelled_by")).isEqualTo(caller.toString());
    }

    private long nombre(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }
}
