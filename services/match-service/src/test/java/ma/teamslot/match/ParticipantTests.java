package ma.teamslot.match;

import static ma.teamslot.match.MatchCreationTests.appelant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** SCRUM-60 : POST /api/v1/matches/{id}/participants, un test par critere d'acceptation. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MongoDeTest.class)
class ParticipantTests {

    @Autowired MockMvc mvc;
    @Autowired MongoTemplate mongo;

    private UUID organisateur;
    private UUID matchId;

    @BeforeEach
    void preparer() {
        donnees().vider();
        organisateur = UUID.randomUUID();
        matchId = donnees().creerMatch(organisateur, "PENDING_PAYMENT");
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

        assertThat(donnees().participants()).hasSize(1);
        Document participant = donnees().participants().getFirst();
        assertThat(participant).containsEntry("userId", ami.toString()).containsEntry("status", "INVITED")
                .containsEntry("addedBy", organisateur.toString());
        assertThat(participant.get("guestName")).isNull();

        assertThat(donnees().evenements()).hasSize(1);
        Document evenement = donnees().evenements().getFirst();
        JsonNode data = DonneesMongo.payload(evenement).get("data");
        assertThat(evenement).containsEntry("eventType", "match.participant_added")
                .containsEntry("aggregateId", matchId.toString());
        assertThat(data.get("userId").asString()).isEqualTo(ami.toString());
        assertThat(data.get("participantId")).isNotNull();
    }

    @Test
    void l_organisateur_ajoute_un_invite_sans_compte_confirme() throws Exception {
        ajouter(organisateur, matchId, "{\"guestName\":\"  Yassir  \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.guestName").value("Yassir"))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(donnees().participants()).singleElement()
                .satisfies(p -> assertThat(p.getString("guestName")).isEqualTo("Yassir"));
        assertThat(donnees().evenements()).singleElement()
                .satisfies(e -> assertThat(DonneesMongo.payload(e).get("data").get("guestName").asString())
                        .isEqualTo("Yassir"));
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

        assertThat(donnees().evenements()).hasSize(1);
        String evenement = donnees().evenements().getFirst().getString("payload");
        assertThat(evenement).doesNotContain("0612345678").doesNotContain("yassir@mail.ma");
        assertThat(donnees().match(matchId).toJson()).doesNotContain("0612345678").doesNotContain("yassir@mail.ma");
    }

    @Test
    void un_e_mail_ou_un_numero_a_la_place_du_surnom_400() throws Exception {
        ajouter(organisateur, matchId, "{\"guestName\":\"yassir@mail.ma\"}").andExpect(status().isBadRequest());
        ajouter(organisateur, matchId, "{\"guestName\":\"06 12 34 56 78\"}").andExpect(status().isBadRequest());
        assertThat(nombre("participants")).isZero();
    }

    @Test
    void un_participant_n_a_aucun_champ_pour_un_numero_ou_un_e_mail() throws Exception {
        ajouter(organisateur, matchId, "{\"guestName\":\"Yassir\",\"phone\":\"0612345678\"}")
                .andExpect(status().isCreated());

        // side (SCRUM-61) : côté A ou B du tirage au sort ; guestKey : surnom sans espaces ni casse (unicité).
        // Le match est le document parent : plus de champ match_id dans le participant.
        assertThat(donnees().participants().getFirst().keySet()).containsExactlyInAnyOrder("addedBy", "createdAt",
                "guestKey", "guestName", "id", "side", "status", "userId");
    }

    private void changerStatut(String statut) {
        donnees().changerStatut(matchId, statut);
    }

    private DonneesMongo donnees() {
        return new DonneesMongo(mongo);
    }

    private long nombre(String collection) {
        return "participants".equals(collection) ? donnees().nombreParticipants() : donnees().nombreEvenements();
    }
}
