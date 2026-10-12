package ma.teamslot.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** SCRUM-62 : payer en ligne ou sur place, un test par critere d'acceptation, plus S6. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentApiTests {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    private UUID payeur;
    private UUID matchId;

    @BeforeEach
    void preparer() {
        jdbc.sql("DELETE FROM payments").update();
        jdbc.sql("DELETE FROM outbox_event").update();
        payeur = UUID.randomUUID();
        matchId = UUID.randomUUID();
    }

    /** Paiement en attente, tel que cree a la reception de match.created. */
    private void paiementEnAttente(boolean surPlaceAccepte) {
        jdbc.sql("""
                INSERT INTO payments (id, match_id, payer_id, total_amount_cents, currency,
                                      accepts_on_site_payment, status)
                VALUES (:id, :match, :payeur, 30000, 'MAD', :surPlace, 'PENDING')
                """)
                .param("id", UUID.randomUUID()).param("match", matchId).param("payeur", payeur)
                .param("surPlace", surPlaceAccepte).update();
    }

    private ResultActions appeler(MockHttpServletRequestBuilder requete, UUID appelant) throws Exception {
        return mvc.perform(requete.with(jwt().jwt(j -> j.subject(appelant.toString()))));
    }

    // Critere 1 : en ligne (simule), le paiement est complete et payment.completed est publie

    @Test
    void le_payeur_paie_en_ligne_et_payment_completed_est_publie() throws Exception {
        paiementEnAttente(false);

        appeler(post("/api/v1/payments/" + matchId + "/online"), payeur)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.mode").value("ONLINE"))
                .andExpect(jsonPath("$.totalAmount.amount").value(30000))
                .andExpect(jsonPath("$.totalAmount.currency").value("MAD"));

        assertThat(statut()).isEqualTo("COMPLETED");
        Map<String, Object> evenement = evenementUnique();
        assertThat(evenement).containsEntry("event_type", "payment.completed")
                .containsEntry("aggregate_id", matchId).containsEntry("mode", "ONLINE")
                .containsEntry("total", "30000").containsEntry("payer_id", payeur.toString());
    }

    // Critere 2 : sur place seulement si le terrain l'accepte, sinon 409

    @Test
    void sur_place_accepte_par_le_terrain_publie_payment_on_site_accepted() throws Exception {
        paiementEnAttente(true);

        appeler(post("/api/v1/payments/" + matchId + "/on-site"), payeur)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_SITE_ACCEPTED"))
                .andExpect(jsonPath("$.mode").value("ON_SITE"));

        assertThat(statut()).isEqualTo("ON_SITE_ACCEPTED");
        assertThat(evenementUnique()).containsEntry("event_type", "payment.on_site_accepted")
                .containsEntry("total", "30000");
    }

    @Test
    void sur_place_refuse_par_le_terrain_409_sans_changement_ni_evenement() throws Exception {
        paiementEnAttente(false);

        appeler(post("/api/v1/payments/" + matchId + "/on-site"), payeur)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://teamslot.app/problems/on-site-payment-not-accepted"));

        assertThat(statut()).isEqualTo("PENDING");
        assertThat(nombreEvenements()).isZero();
    }

    // Critere 3 : seul le responsable du paiement ; montant recalcule par le serveur

    @Test
    void une_autre_personne_recoit_403_sans_changement_ni_evenement() throws Exception {
        paiementEnAttente(true);

        appeler(post("/api/v1/payments/" + matchId + "/online"), UUID.randomUUID())
                .andExpect(status().isForbidden());
        appeler(post("/api/v1/payments/" + matchId + "/on-site"), UUID.randomUUID())
                .andExpect(status().isForbidden());

        assertThat(statut()).isEqualTo("PENDING");
        assertThat(nombreEvenements()).isZero();
    }

    @Test
    void un_montant_envoye_par_le_client_est_ignore() throws Exception {
        paiementEnAttente(false);

        appeler(post("/api/v1/payments/" + matchId + "/online")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":1,\"totalAmount\":{\"amount\":1,\"currency\":\"MAD\"}}"), payeur)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount.amount").value(30000));

        assertThat(evenementUnique()).containsEntry("total", "30000");
    }

    @Test
    void sans_jeton_401() throws Exception {
        paiementEnAttente(false);

        mvc.perform(post("/api/v1/payments/" + matchId + "/online")).andExpect(status().isUnauthorized());

        assertThat(statut()).isEqualTo("PENDING");
    }

    // S6 (modele de menaces) : rejeu de la meme requete de paiement, un seul paiement

    @Test
    void s6_payer_deux_fois_409_et_un_seul_evenement() throws Exception {
        paiementEnAttente(true);

        appeler(post("/api/v1/payments/" + matchId + "/online"), payeur).andExpect(status().isOk());
        appeler(post("/api/v1/payments/" + matchId + "/online"), payeur).andExpect(status().isConflict());
        appeler(post("/api/v1/payments/" + matchId + "/on-site"), payeur).andExpect(status().isConflict());

        assertThat(statut()).isEqualTo("COMPLETED");
        assertThat(nombreEvenements()).isEqualTo(1);
    }

    // Lecture et cas d'erreur

    @Test
    void match_sans_paiement_404() throws Exception {
        appeler(post("/api/v1/payments/" + UUID.randomUUID() + "/online"), payeur)
                .andExpect(status().isNotFound());
    }

    @Test
    void le_payeur_consulte_son_paiement_les_autres_403() throws Exception {
        paiementEnAttente(false);

        appeler(get("/api/v1/payments/" + matchId), payeur)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.payerId").value(payeur.toString()));
        appeler(get("/api/v1/payments/" + matchId), UUID.randomUUID())
                .andExpect(status().isForbidden());
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
                SELECT aggregate_id, event_type, payload->'data'->>'mode' AS mode,
                       payload->'data'->>'totalAmount' AS total, payload->'data'->>'payerId' AS payer_id
                FROM outbox_event
                """).query().singleRow();
    }
}
