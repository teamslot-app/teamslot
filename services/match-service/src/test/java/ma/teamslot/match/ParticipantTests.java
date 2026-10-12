package ma.teamslot.match;

import static ma.teamslot.match.MatchCreationTests.appelant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
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

/** SCRUM-60 : POST /api/v1/matches/{id}/participants, un test par critere d'acceptation. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ParticipantTests {

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
        matchId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO matches (id, kind, status, visibility, owner_id, payer_id, venue_id,
                                     slot_id, starts_at, total_amount_cents, currency)
                VALUES (:id, 'FRIENDLY', 'PENDING_PAYMENT', 'PRIVATE', :owner, :owner, :venue,
                        :slot, :debut, 30000, 'MAD')
                """)
                .param("id", matchId).param("owner", organisateur)
                .param("venue", UUID.randomUUID()).param("slot", UUID.randomUUID())
                .param("debut", OffsetDateTime.parse("2026-10-20T18:00:00Z"))
                .update();
    }

    private ResultActions ajouter(UUID qui, UUID match, String corps) throws Exception {
        return mvc.perform(post("/api/v1/matches/" + match + "/participants")
                .with(appelant(qui))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps));
    }

    // Critere 1 : un compte (invite a confirmer) ou un surnom, jamais les deux

    @Test
    void l_organisateur_ajoute_un_compte_invite_et_publie_l_evenement() throws Exception {
        UUID ami = UUID.randomUUID();

        ajouter(organisateur, matchId, "{\"userId\":\"" + ami + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.matchId").value(matchId.toString()))
                .andExpect(jsonPath("$.userId").value(ami.toString()))
                .andExpect(jsonPath("$.guestName").doesNotExist())
                .andExpect(jsonPath("$.status").value("INVITED"));

        Map<String, Object> ligne = jdbc.sql("SELECT user_id, guest_name, status, added_by FROM participants")
                .query().singleRow();
        assertThat(ligne).containsEntry("user_id", ami).containsEntry("status", "INVITED")
                .containsEntry("added_by", organisateur);
        assertThat(ligne.get("guest_name")).isNull();

        Map<String, Object> evenement = jdbc.sql("""
                SELECT aggregate_id, event_type, payload->'data'->>'userId' AS user_id,
                       payload->'data'->>'participantId' AS participant_id
                FROM outbox_event
                """).query().singleRow();
        assertThat(evenement).containsEntry("event_type", "match.participant_added")
                .containsEntry("aggregate_id", matchId).containsEntry("user_id", ami.toString());
        assertThat(evenement.get("participant_id")).isNotNull();
    }

    @Test
    void l_organisateur_ajoute_un_invite_sans_compte_confirme() throws Exception {
        ajouter(organisateur, matchId, "{\"guestName\":\"  Yassir  \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.guestName").value("Yassir"))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(jdbc.sql("SELECT guest_name FROM participants").query(String.class).single())
                .isEqualTo("Yassir");
        assertThat(jdbc.sql("SELECT payload->'data'->>'guestName' FROM outbox_event")
                .query(String.class).single()).isEqualTo("Yassir");
    }

    @Test
    void compte_et_surnom_ensemble_400() throws Exception {
        ajouter(organisateur, matchId, "{\"userId\":\"" + UUID.randomUUID() + "\",\"guestName\":\"Yassir\"}")
                .andExpect(status().isBadRequest());
        assertThat(nombre("participants")).isZero();
    }

    @Test
    void ni_compte_ni_surnom_400() throws Exception {
        ajouter(organisateur, matchId, "{}").andExpect(status().isBadRequest());
        assertThat(nombre("participants")).isZero();
    }

    @Test
    void le_meme_compte_deux_fois_409_sans_second_evenement() throws Exception {
        String corps = "{\"userId\":\"" + UUID.randomUUID() + "\"}";
        ajouter(organisateur, matchId, corps).andExpect(status().isCreated());

        ajouter(organisateur, matchId, corps).andExpect(status().isConflict());

        assertThat(nombre("participants")).isEqualTo(1);
        assertThat(nombre("outbox_event")).isEqualTo(1);
    }

    @Test
    void un_surnom_deja_utilise_409_sans_tenir_compte_de_la_casse_ni_des_espaces() throws Exception {
        ajouter(organisateur, matchId, "{\"guestName\":\"Yassir\"}").andExpect(status().isCreated());

        ajouter(organisateur, matchId, "{\"guestName\":\"  yassir \"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://teamslot.app/problems/participant-already-added"))
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("surnom")));
        ajouter(organisateur, matchId, "{\"guestName\":\"YAS SIR\"}").andExpect(status().isConflict());
        ajouter(organisateur, matchId, "{\"guestName\":\"Yassir 2\"}").andExpect(status().isCreated());

        assertThat(nombre("participants")).isEqualTo(2);
        assertThat(nombre("outbox_event")).isEqualTo(2);
    }

    @Test
    void l_organisateur_ne_peut_pas_s_ajouter_lui_meme_409() throws Exception {
        ajouter(organisateur, matchId, "{\"userId\":\"" + organisateur + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("organisateur")));
        assertThat(nombre("participants")).isZero();
    }

    @Test
    void match_annule_ou_joue_409_sans_rien_enregistrer() throws Exception {
        for (String statut : List.of("CANCELLED", "PLAYED")) {
            changerStatut(statut);
            ajouter(organisateur, matchId, "{\"guestName\":\"Yassir\"}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.type").value("https://teamslot.app/problems/match-closed"));
        }
        assertThat(nombre("participants")).isZero();
        assertThat(nombre("outbox_event")).isZero();
    }

    @Test
    void match_confirme_accepte_encore_des_participants() throws Exception {
        changerStatut("CONFIRMED");
        ajouter(organisateur, matchId, "{\"guestName\":\"Yassir\"}").andExpect(status().isCreated());
    }

    // Critere 2 : seul l'organisateur ajoute des participants, les autres recoivent 403

    @Test
    void une_autre_personne_recoit_403_sans_rien_enregistrer() throws Exception {
        ajouter(UUID.randomUUID(), matchId, "{\"guestName\":\"Yassir\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://teamslot.app/problems/not-match-organizer"));

        assertThat(nombre("participants")).isZero();
        assertThat(nombre("outbox_event")).isZero();
    }

    @Test
    void sans_jeton_401() throws Exception {
        mvc.perform(post("/api/v1/matches/" + matchId + "/participants")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"guestName\":\"Yassir\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(nombre("participants")).isZero();
    }

    @Test
    void match_inexistant_404() throws Exception {
        ajouter(organisateur, UUID.randomUUID(), "{\"guestName\":\"Yassir\"}")
                .andExpect(status().isNotFound());
    }

    // Critere 3 : aucun numero ni e-mail stocke pour un invite sans compte

    @Test
    void numero_et_e_mail_envoyes_par_le_client_sont_ignores() throws Exception {
        ajouter(organisateur, matchId,
                "{\"guestName\":\"Yassir\",\"phone\":\"0612345678\",\"email\":\"yassir@mail.ma\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());

        String evenement = jdbc.sql("SELECT payload::text FROM outbox_event").query(String.class).single();
        assertThat(evenement).doesNotContain("0612345678").doesNotContain("yassir@mail.ma");
    }

    @Test
    void un_e_mail_ou_un_numero_a_la_place_du_surnom_400() throws Exception {
        ajouter(organisateur, matchId, "{\"guestName\":\"yassir@mail.ma\"}").andExpect(status().isBadRequest());
        ajouter(organisateur, matchId, "{\"guestName\":\"06 12 34 56 78\"}").andExpect(status().isBadRequest());
        assertThat(nombre("participants")).isZero();
    }

    @Test
    void la_table_n_a_aucune_colonne_pour_un_numero_ou_un_e_mail() {
        List<String> colonnes = jdbc.sql("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'participants' ORDER BY column_name
                """).query(String.class).list();

        // side (SCRUM-61, V5) : côté A ou B du tirage au sort, aucune donnée personnelle.
        assertThat(colonnes).containsExactly("added_by", "created_at", "guest_name", "id",
                "match_id", "side", "status", "user_id");
    }

    private void changerStatut(String statut) {
        jdbc.sql("UPDATE matches SET status = :s WHERE id = :id").param("s", statut).param("id", matchId).update();
    }

    private long nombre(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }
}
