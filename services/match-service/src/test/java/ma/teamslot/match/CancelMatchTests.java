package ma.teamslot.match;

import static ma.teamslot.match.MatchCreationTests.appelant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** SCRUM-76 : POST /api/v1/matches/{id}/cancel, un test par critère d'acceptation. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MongoDeTest.class)
class CancelMatchTests {

    @Autowired MockMvc mvc;
    @Autowired MongoTemplate mongo;

    private UUID organisateur;

    @BeforeEach
    void preparer() {
        donnees().vider();
        organisateur = UUID.randomUUID();
    }

    private DonneesMongo donnees() {
        return new DonneesMongo(mongo);
    }

    private ResultActions annuler(UUID qui, UUID match) throws Exception {
        return mvc.perform(post("/api/v1/matches/" + match + "/cancel").with(appelant(qui)));
    }

    private String statut(UUID match) {
        return donnees().match(match).getString("status");
    }

    // Critère 1 : l'organisateur annule, le match passe à CANCELLED et match.cancelled est publié

    @Test
    void l_organisateur_annule_un_match_en_attente_de_paiement_et_publie_match_cancelled() throws Exception {
        UUID match = donnees().creerMatch(organisateur, "PENDING_PAYMENT");

        annuler(organisateur, match).andExpect(status().isNoContent());

        assertThat(statut(match)).isEqualTo("CANCELLED");
        assertThat(((Number) donnees().match(match).get("version")).longValue()).isEqualTo(1L);
        List<Document> evenements = donnees().evenementsDe(match);
        assertThat(evenements).hasSize(1);
        assertThat(evenements.getFirst().getString("eventType")).isEqualTo("match.cancelled");
        JsonNode data = DonneesMongo.payload(evenements.getFirst()).get("data");
        assertThat(data.get("matchId").asString()).isEqualTo(match.toString());
        assertThat(data.get("cancelledBy").asString()).isEqualTo(organisateur.toString());
        assertThat(data.get("reason").asString()).isEqualTo("ORGANIZER_CANCELLED");
    }

    @Test
    void un_match_confirme_peut_etre_annule() throws Exception {
        UUID match = donnees().creerMatch(organisateur, "CONFIRMED");

        annuler(organisateur, match).andExpect(status().isNoContent());

        assertThat(statut(match)).isEqualTo("CANCELLED");
        assertThat(donnees().evenementsDe(match)).hasSize(1);
    }

    @Test
    void aucune_donnee_personnelle_des_invites_dans_l_evenement() throws Exception {
        UUID match = donnees().creerMatch(organisateur, "CONFIRMED");
        donnees().ajouterParticipant(match, null, "Yassir", "CONFIRMED", organisateur);

        annuler(organisateur, match).andExpect(status().isNoContent());

        assertThat(donnees().evenementsDe(match).getFirst().getString("payload")).doesNotContain("Yassir");
    }

    // Critère 3 : seul l'organisateur annule (403), un match joué ou déjà annulé ne l'est pas (409)

    @Test
    void une_autre_personne_recoit_403_sans_rien_changer() throws Exception {
        UUID match = donnees().creerMatch(organisateur, "CONFIRMED");

        annuler(UUID.randomUUID(), match)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://teamslot.app/problems/not-match-organizer"));

        assertThat(statut(match)).isEqualTo("CONFIRMED");
        assertThat(donnees().nombreEvenements()).isZero();
    }

    @Test
    void un_match_joue_ou_deja_annule_409_sans_evenement() throws Exception {
        for (String statut : List.of("PLAYED", "CANCELLED")) {
            UUID match = donnees().creerMatch(organisateur, statut);

            annuler(organisateur, match)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.type").value("https://teamslot.app/problems/match-not-cancellable"));

            assertThat(statut(match)).isEqualTo(statut);
        }
        assertThat(donnees().nombreEvenements()).isZero();
    }

    @Test
    void annuler_deux_fois_publie_un_seul_match_cancelled() throws Exception {
        UUID match = donnees().creerMatch(organisateur, "PENDING_PAYMENT");

        annuler(organisateur, match).andExpect(status().isNoContent());
        annuler(organisateur, match).andExpect(status().isConflict());

        assertThat(donnees().evenementsDe(match)).hasSize(1);
    }

    @Test
    void match_introuvable_404_et_sans_jeton_401() throws Exception {
        annuler(organisateur, UUID.randomUUID()).andExpect(status().isNotFound());

        UUID match = donnees().creerMatch(organisateur, "CONFIRMED");
        mvc.perform(post("/api/v1/matches/" + match + "/cancel")).andExpect(status().isUnauthorized());
        // L'identité vient du JWT : un en-tête X-User-Id ne suffit pas
        mvc.perform(post("/api/v1/matches/" + match + "/cancel").header("X-User-Id", organisateur.toString()))
                .andExpect(status().isUnauthorized());
        assertThat(statut(match)).isEqualTo("CONFIRMED");
    }
}
