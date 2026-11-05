package ma.teamslot.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * SCRUM-75 : le gérant du terrain confirme l'encaissement sur place. Un test par critère d'acceptation,
 * plus le test de sécurité S14 (un joueur ou le gérant d'un autre terrain : 403).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EncaissementSurPlaceTests {

    private static final String GERANT = "VENUE_MANAGER";
    private static final String JOUEUR = "PLAYER";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    private UUID payeur;
    private UUID gerant;
    private UUID terrain;
    private UUID matchId;

    @BeforeEach
    void preparer() {
        jdbc.sql("DELETE FROM payments").update();
        jdbc.sql("DELETE FROM venue_owners").update();
        jdbc.sql("DELETE FROM outbox_event").update();
        payeur = UUID.randomUUID();
        gerant = UUID.randomUUID();
        terrain = UUID.randomUUID();
        matchId = UUID.randomUUID();
        // Gérant du terrain, tel qu'appris de venue.created
        jdbc.sql("INSERT INTO venue_owners (venue_id, owner_id) VALUES (:v, :o)")
                .param("v", terrain).param("o", gerant).update();
    }

    /** Paiement du match sur ce terrain, dans l'état demandé. */
    private void paiement(String statut, String mode, UUID venueId) {
        jdbc.sql("""
                INSERT INTO payments (id, match_id, payer_id, total_amount_cents, currency,
                                      accepts_on_site_payment, mode, status, venue_id)
                VALUES (:id, :match, :payeur, 30000, 'MAD', true, :mode, :statut, :terrain)
                """)
                .param("id", UUID.randomUUID()).param("match", matchId).param("payeur", payeur)
                .param("mode", mode, java.sql.Types.VARCHAR).param("statut", statut)
                .param("terrain", venueId, java.sql.Types.OTHER).update();
    }

    private ResultActions encaisser(UUID appelant, String role) throws Exception {
        return appeler(post("/api/v1/payments/" + matchId + "/collected"), appelant, role);
    }

    private ResultActions appeler(MockHttpServletRequestBuilder requete, UUID appelant, String role)
            throws Exception {
        return mvc.perform(requete.with(jwt().jwt(j -> {
            j.subject(appelant.toString());
            if (role != null) {
                j.claim("role", role);
            }
        })));
    }

    // Critère 1 : le gérant du terrain confirme ; COLLECTED ; payment.collected_on_site publié (outbox)

    @Test
    void le_gerant_du_terrain_confirme_et_payment_collected_on_site_est_publie() throws Exception {
        paiement("ON_SITE_ACCEPTED", "ON_SITE", terrain);

        encaisser(gerant, GERANT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COLLECTED"))
                .andExpect(jsonPath("$.mode").value("ON_SITE"))
                .andExpect(jsonPath("$.collectedBy").value(gerant.toString()))
                .andExpect(jsonPath("$.collectedAt").isNotEmpty())
                .andExpect(jsonPath("$.totalAmount.amount").value(30000));

        Map<String, Object> ligne = jdbc.sql("SELECT status, collected_by FROM payments WHERE match_id = :m")
                .param("m", matchId).query().singleRow();
        assertThat(ligne).containsEntry("status", "COLLECTED").containsEntry("collected_by", gerant);
        Map<String, Object> evenement = evenementUnique();
        assertThat(evenement).containsEntry("event_type", "payment.collected_on_site")
                .containsEntry("aggregate_id", matchId).containsEntry("collected_by", gerant.toString());
        assertThat(evenement.get("collected_at")).isNotNull();
    }

    // Critère 2 (S14) : un joueur, ou le gérant d'un autre terrain : 403, sans changement ni événement

    @Test
    void s14_un_joueur_meme_le_payeur_recoit_403() throws Exception {
        paiement("ON_SITE_ACCEPTED", "ON_SITE", terrain);

        encaisser(payeur, JOUEUR).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://teamslot.app/problems/not-venue-manager"));
        encaisser(UUID.randomUUID(), JOUEUR).andExpect(status().isForbidden());

        assertThat(statut()).isEqualTo("ON_SITE_ACCEPTED");
        assertThat(nombreEvenements()).isZero();
    }

    @Test
    void s14_le_gerant_d_un_autre_terrain_recoit_403() throws Exception {
        paiement("ON_SITE_ACCEPTED", "ON_SITE", terrain);
        UUID autreGerant = UUID.randomUUID();
        jdbc.sql("INSERT INTO venue_owners (venue_id, owner_id) VALUES (:v, :o)")
                .param("v", UUID.randomUUID()).param("o", autreGerant).update();

        encaisser(autreGerant, GERANT).andExpect(status().isForbidden());

        assertThat(statut()).isEqualTo("ON_SITE_ACCEPTED");
        assertThat(nombreEvenements()).isZero();
    }

    // Sécurité : rôle et identité lus dans le JWT vérifié, refus par défaut

    @Test
    void le_bon_sub_sans_role_gerant_dans_le_jeton_recoit_403() throws Exception {
        paiement("ON_SITE_ACCEPTED", "ON_SITE", terrain);

        encaisser(gerant, null).andExpect(status().isForbidden());
        encaisser(gerant, JOUEUR).andExpect(status().isForbidden());

        assertThat(statut()).isEqualTo("ON_SITE_ACCEPTED");
    }

    @Test
    void terrain_inconnu_de_payment_403_refus_par_defaut() throws Exception {
        paiement("ON_SITE_ACCEPTED", "ON_SITE", null);

        encaisser(gerant, GERANT).andExpect(status().isForbidden());

        assertThat(statut()).isEqualTo("ON_SITE_ACCEPTED");
    }

    @Test
    void sans_jeton_401() throws Exception {
        paiement("ON_SITE_ACCEPTED", "ON_SITE", terrain);

        mvc.perform(post("/api/v1/payments/" + matchId + "/collected")).andExpect(status().isUnauthorized());

        assertThat(statut()).isEqualTo("ON_SITE_ACCEPTED");
    }

    // Critère 3 : un paiement déjà encaissé ne peut pas l'être deux fois (409) ; seul ON_SITE_ACCEPTED passe

    @Test
    void encaisser_deux_fois_409_et_un_seul_evenement() throws Exception {
        paiement("ON_SITE_ACCEPTED", "ON_SITE", terrain);

        encaisser(gerant, GERANT).andExpect(status().isOk());
        encaisser(gerant, GERANT).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://teamslot.app/problems/payment-not-on-site-accepted"));

        assertThat(statut()).isEqualTo("COLLECTED");
        assertThat(nombreEvenements()).isEqualTo(1);
    }

    @Test
    void un_paiement_en_attente_ou_paye_en_ligne_ne_peut_pas_etre_encaisse_409() throws Exception {
        paiement("PENDING", null, terrain);
        encaisser(gerant, GERANT).andExpect(status().isConflict());
        assertThat(statut()).isEqualTo("PENDING");

        jdbc.sql("UPDATE payments SET mode = 'ONLINE', status = 'COMPLETED' WHERE match_id = :m")
                .param("m", matchId).update();
        encaisser(gerant, GERANT).andExpect(status().isConflict());
        assertThat(statut()).isEqualTo("COMPLETED");
        assertThat(nombreEvenements()).isZero();
    }

    @Test
    void un_montant_envoye_par_le_client_est_ignore() throws Exception {
        paiement("ON_SITE_ACCEPTED", "ON_SITE", terrain);

        appeler(post("/api/v1/payments/" + matchId + "/collected")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":1,\"totalAmount\":{\"amount\":1,\"currency\":\"MAD\"}}"), gerant, GERANT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount.amount").value(30000));

        assertThat(jdbc.sql("SELECT total_amount_cents FROM payments WHERE match_id = :m")
                .param("m", matchId).query(Long.class).single()).isEqualTo(30000L);
    }

    @Test
    void match_sans_paiement_404() throws Exception {
        encaisser(gerant, GERANT).andExpect(status().isNotFound());
    }

    private String statut() {
        return jdbc.sql("SELECT status FROM payments WHERE match_id = :m").param("m", matchId)
                .query(String.class).single();
    }

    private long nombreEvenements() {
        return jdbc.sql("SELECT count(*) FROM outbox_event").query(Long.class).single();
    }

    private Map<String, Object> evenementUnique() {
        return jdbc.sql("""
                SELECT aggregate_id, event_type, payload->'data'->>'collectedBy' AS collected_by,
                       payload->'data'->>'collectedAt' AS collected_at
                FROM outbox_event
                """).query().singleRow();
    }
}
