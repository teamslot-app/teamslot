package ma.teamslot.match;

import static ma.teamslot.match.MatchCreationTests.appelant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** SCRUM-61 : POST /matches/{id}/split et GET /matches/{id}/participants, un test par règle. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeamSplitTests {

    private static final String EVENEMENT = "SELECT count(*) FROM outbox_event WHERE event_type = 'match.teams_split'";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    private UUID organisateur;
    private UUID matchId;

    @BeforeEach
    void preparer() {
        jdbc.sql("DELETE FROM participants").update();
        jdbc.sql("DELETE FROM outbox_event").update();
        jdbc.sql("DELETE FROM matches").update();
        organisateur = UUID.randomUUID();
        matchId = creerMatch("CONFIRMED");
    }

    private UUID creerMatch(String statut) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO matches (id, kind, status, visibility, owner_id, payer_id, venue_id,
                                     slot_id, starts_at, total_amount_cents, currency)
                VALUES (:id, 'FRIENDLY', :statut, 'PRIVATE', :owner, :owner, :venue,
                        :slot, :debut, 30000, 'MAD')
                """)
                .param("id", id).param("statut", statut).param("owner", organisateur)
                .param("venue", UUID.randomUUID()).param("slot", UUID.randomUUID())
                .param("debut", OffsetDateTime.parse("2026-10-20T18:00:00Z"))
                .update();
        return id;
    }

    private void inserer(UUID match, UUID compte, String surnom, String statut) {
        jdbc.sql("""
                INSERT INTO participants (id, match_id, user_id, guest_name, status, added_by)
                VALUES (:id, :match, :compte, :surnom, :statut, :ajoutePar)
                """)
                .param("id", UUID.randomUUID()).param("match", match)
                .param("compte", compte, Types.OTHER).param("surnom", surnom, Types.VARCHAR)
                .param("statut", statut).param("ajoutePar", organisateur)
                .update();
    }

    private UUID ajouterCompte(UUID match, String statut) {
        UUID compte = UUID.randomUUID();
        inserer(match, compte, null, statut);
        return compte;
    }

    private ResultActions repartir(UUID qui, UUID match, String strategie) throws Exception {
        return mvc.perform(post("/api/v1/matches/" + match + "/split")
                .param("strategy", strategie).with(appelant(qui)));
    }

    private ResultActions lister(UUID qui, UUID match) throws Exception {
        return mvc.perform(get("/api/v1/matches/" + match + "/participants").with(appelant(qui)));
    }

    private long nombre(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    // Répartition

    @Test
    void repartit_comptes_et_invites_en_deux_cotes_equilibres_et_publie_l_evenement() throws Exception {
        ajouterCompte(matchId, "INVITED");
        ajouterCompte(matchId, "CONFIRMED");
        inserer(matchId, null, "Yassir", "CONFIRMED");
        UUID demande = ajouterCompte(matchId, "REQUESTED");
        ajouterCompte(matchId, "DECLINED");

        repartir(organisateur, matchId, "random")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchId").value(matchId.toString()))
                .andExpect(jsonPath("$.strategy").value("random"))
                .andExpect(jsonPath("$.sideA.length()").value(2))
                .andExpect(jsonPath("$.sideB.length()").value(2))
                .andExpect(jsonPath("$.sideA[*].side", Matchers.everyItem(Matchers.is("A"))))
                .andExpect(jsonPath("$.sideB[*].side", Matchers.everyItem(Matchers.is("B"))));

        // 4 tirés : l'organisateur, un INVITED, un CONFIRMED, Yassir. REQUESTED et DECLINED restent sans côté.
        assertThat(nombre("SELECT count(*) FROM participants WHERE side IS NOT NULL")).isEqualTo(4);
        assertThat(nombre("SELECT count(*) FROM participants WHERE side = 'A'")).isEqualTo(2);
        assertThat(nombre("SELECT count(*) FROM participants WHERE user_id = '" + demande + "' AND side IS NULL"))
                .isEqualTo(1);
        assertThat(nombre("SELECT count(*) FROM participants WHERE user_id = '" + organisateur
                + "' AND status = 'CONFIRMED'")).isEqualTo(1);

        assertThat(nombre(EVENEMENT)).isEqualTo(1);
        assertThat(jdbc.sql("SELECT payload->'data'->>'strategy' FROM outbox_event").query(String.class).single())
                .isEqualTo("random");
        String data = jdbc.sql("SELECT payload->'data' FROM outbox_event").query(String.class).single();
        assertThat(data).contains("guest:Yassir").contains("user:" + organisateur)
                .doesNotContain(demande.toString());
    }

    @Test
    void un_nouveau_tirage_remplace_les_cotes_sans_dedoubler_l_organisateur() throws Exception {
        ajouterCompte(matchId, "INVITED");
        ajouterCompte(matchId, "INVITED");
        repartir(organisateur, matchId, "random").andExpect(status().isOk());

        ajouterCompte(matchId, "CONFIRMED"); // ami ajouté après le premier tirage
        assertThat(nombre("SELECT count(*) FROM participants WHERE side IS NULL")).isEqualTo(1);

        repartir(organisateur, matchId, "random")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sideA.length()").value(2))
                .andExpect(jsonPath("$.sideB.length()").value(2));

        assertThat(nombre("SELECT count(*) FROM participants WHERE side IS NOT NULL")).isEqualTo(4);
        assertThat(nombre("SELECT count(*) FROM participants WHERE user_id = '" + organisateur + "'")).isEqualTo(1);
        assertThat(nombre(EVENEMENT)).isEqualTo(2);
    }

    @Test
    void un_autre_que_l_organisateur_403_sans_rien_changer() throws Exception {
        ajouterCompte(matchId, "CONFIRMED");

        repartir(UUID.randomUUID(), matchId, "random").andExpect(status().isForbidden());

        assertThat(nombre("SELECT count(*) FROM participants")).isEqualTo(1);
        assertThat(nombre("SELECT count(*) FROM participants WHERE side IS NOT NULL")).isZero();
        assertThat(nombre(EVENEMENT)).isZero();
    }

    @Test
    void match_introuvable_404() throws Exception {
        repartir(organisateur, UUID.randomUUID(), "random").andExpect(status().isNotFound());
    }

    @Test
    void match_pas_confirme_409_sans_inscrire_l_organisateur() throws Exception {
        UUID enAttente = creerMatch("PENDING_PAYMENT");
        ajouterCompte(enAttente, "INVITED");
        ajouterCompte(enAttente, "INVITED");

        repartir(organisateur, enAttente, "random").andExpect(status().isConflict());

        assertThat(nombre("SELECT count(*) FROM participants")).isEqualTo(2);
        assertThat(nombre("SELECT count(*) FROM participants WHERE side IS NOT NULL")).isZero();
        assertThat(nombre(EVENEMENT)).isZero();
    }

    @Test
    void organisateur_seul_409_et_son_inscription_est_annulee() throws Exception {
        repartir(organisateur, matchId, "random").andExpect(status().isConflict());

        assertThat(nombre("SELECT count(*) FROM participants")).isZero();
        assertThat(nombre(EVENEMENT)).isZero();
    }

    @Test
    void strategie_inconnue_ou_absente_400_sans_rien_changer() throws Exception {
        ajouterCompte(matchId, "CONFIRMED");

        repartir(organisateur, matchId, "balanced").andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/matches/" + matchId + "/split").with(appelant(organisateur)))
                .andExpect(status().isBadRequest());

        assertThat(nombre("SELECT count(*) FROM participants")).isEqualTo(1);
        assertThat(nombre(EVENEMENT)).isZero();
    }

    // Liste des participants

    @Test
    void l_organisateur_et_un_participant_avec_compte_voient_la_liste_et_les_cotes() throws Exception {
        UUID ami = ajouterCompte(matchId, "INVITED");
        inserer(matchId, null, "Yassir", "CONFIRMED");
        repartir(organisateur, matchId, "random").andExpect(status().isOk());

        for (UUID qui : new UUID[] {organisateur, ami}) {
            lister(qui, matchId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[*].side", Matchers.hasSize(3)));
        }
    }

    @Test
    void un_inconnu_ou_un_compte_refuse_ne_voit_pas_la_liste_403() throws Exception {
        UUID refuse = ajouterCompte(matchId, "DECLINED");

        lister(UUID.randomUUID(), matchId).andExpect(status().isForbidden());
        lister(refuse, matchId).andExpect(status().isForbidden());
    }

    @Test
    void liste_d_un_match_introuvable_404_et_sans_jeton_401() throws Exception {
        lister(organisateur, UUID.randomUUID()).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/matches/" + matchId + "/participants")).andExpect(status().isUnauthorized());
    }
}
