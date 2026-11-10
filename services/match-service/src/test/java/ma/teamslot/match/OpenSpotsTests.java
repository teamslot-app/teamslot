package ma.teamslot.match;

import static ma.teamslot.match.MatchCreationTests.appelant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** SCRUM-78 : POST /api/v1/matches/{id}/open-spots, un test par critère d'acceptation. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MongoDeTest.class)
class OpenSpotsTests {

    private static final String PROBLEMES = "https://teamslot.app/problems/";

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

    private ResultActions ouvrir(UUID qui, UUID match, String corps) throws Exception {
        return mvc.perform(post("/api/v1/matches/" + match + "/open-spots").with(appelant(qui))
                .contentType(MediaType.APPLICATION_JSON).content(corps));
    }

    private ResultActions ouvrir(UUID qui, UUID match, int places) throws Exception {
        return ouvrir(qui, match, "{\"openSpots\":" + places + "}");
    }

    private int placesEnBase(UUID match) {
        return ((Number) donnees().match(match).get("openSpots")).intValue();
    }

    // Critère 1 : l'organisateur indique le nombre de places ; match.spots_opened est publié

    @Test
    void l_organisateur_ouvre_des_places_et_publie_match_spots_opened() throws Exception {
        UUID match = donnees().creerMatch(organisateur, "PENDING_PAYMENT");
        donnees().ajouterParticipant(match, null, "Yassir", "INVITED", organisateur);

        ouvrir(organisateur, match, 3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(match.toString()))
                .andExpect(jsonPath("$.openSpots").value(3))
                .andExpect(jsonPath("$.maxPlayers").value(10))
                .andExpect(jsonPath("$.version").value(1));

        assertThat(placesEnBase(match)).isEqualTo(3);
        List<Document> evenements = donnees().evenementsDe(match);
        assertThat(evenements).hasSize(1);
        assertThat(evenements.getFirst().getString("eventType")).isEqualTo("match.spots_opened");
        JsonNode data = DonneesMongo.payload(evenements.getFirst()).get("data");
        assertThat(data.get("matchId").asString()).isEqualTo(match.toString());
        assertThat(data.get("venueId").asString()).isEqualTo(donnees().match(match).getString("venueId"));
        assertThat(Instant.parse(data.get("startsAt").asString())).isEqualTo(Instant.parse("2026-10-20T18:00:00Z"));
        assertThat(data.get("openSpots").asInt()).isEqualTo(3);
        // Aucun participant dans l'événement (ni surnom d'invité, ni compte)
        assertThat(evenements.getFirst().getString("payload")).doesNotContain("Yassir");
    }

    @Test
    void un_match_confirme_accepte_aussi_et_un_second_appel_remplace_le_nombre() throws Exception {
        UUID match = donnees().creerMatch(organisateur, "CONFIRMED");

        ouvrir(organisateur, match, 3).andExpect(status().isOk());
        ouvrir(organisateur, match, 5).andExpect(status().isOk()).andExpect(jsonPath("$.openSpots").value(5));

        assertThat(placesEnBase(match)).isEqualTo(5);
        assertThat(donnees().evenementsDe(match)).hasSize(2);
    }

    // Critère 3 : nombre négatif ou supérieur à la capacité -> 400

    @Test
    void nombre_nul_negatif_absent_ou_pas_un_entier_400_sans_rien_changer() throws Exception {
        UUID match = donnees().creerMatch(organisateur, "PENDING_PAYMENT");

        for (String corps : List.of("{\"openSpots\":0}", "{\"openSpots\":-2}", "{}", "{\"openSpots\":\"3\"x}")) {
            ouvrir(organisateur, match, corps).andExpect(status().isBadRequest());
        }

        assertThat(placesEnBase(match)).isZero();
        assertThat(donnees().nombreEvenements()).isZero();
    }

    @Test
    void match_cree_avant_maxPlayers_compte_10_joueurs_organisateur_compris() throws Exception {
        // creerMatch n'écrit pas maxPlayers : comme un document créé avant SCRUM-78
        UUID match = donnees().creerMatch(organisateur, "PENDING_PAYMENT");
        assertThat(donnees().match(match)).doesNotContainKey("maxPlayers");
        donnees().ajouterParticipant(match, UUID.randomUUID(), null, "INVITED", organisateur);
        donnees().ajouterParticipant(match, null, "Yassir", "CONFIRMED", organisateur);
        // 3 joueurs (organisateur + 2) : 7 places au plus

        ouvrir(organisateur, match, 8)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMES + "invalid-open-spots"))
                .andExpect(jsonPath("$.maxOpenSpots").value(7));
        assertThat(donnees().nombreEvenements()).isZero();

        ouvrir(organisateur, match, 7).andExpect(status().isOk()).andExpect(jsonPath("$.maxPlayers").value(10));
    }

    @Test
    void l_organisateur_tire_au_sort_n_est_compte_qu_une_fois() throws Exception {
        UUID match = donnees().creerMatch(organisateur, "CONFIRMED");
        // Après une répartition, l'organisateur est dans le tableau (SplitTeamsService)
        donnees().ajouterParticipant(match, organisateur, null, "CONFIRMED", organisateur);
        donnees().ajouterParticipant(match, null, "Yassir", "CONFIRMED", organisateur);

        ouvrir(organisateur, match, 9).andExpect(status().isBadRequest());
        ouvrir(organisateur, match, 8).andExpect(status().isOk());
    }

    @Test
    void demandes_et_refus_n_occupent_pas_de_place() throws Exception {
        UUID match = donnees().creerMatch(organisateur, "CONFIRMED");
        donnees().ajouterParticipant(match, UUID.randomUUID(), null, "REQUESTED", organisateur);
        donnees().ajouterParticipant(match, UUID.randomUUID(), null, "DECLINED", organisateur);

        ouvrir(organisateur, match, 9).andExpect(status().isOk());
    }

    @Test
    void match_complet_400_avec_un_message_clair() throws Exception {
        UUID match = donnees().creerMatch(organisateur, "CONFIRMED");
        for (int i = 1; i <= 9; i++) {
            donnees().ajouterParticipant(match, null, "Joueur " + i, "INVITED", organisateur);
        }

        ouvrir(organisateur, match, 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maxOpenSpots").value(0))
                .andExpect(jsonPath("$.detail").value("Le match est déjà complet : aucune place à ouvrir."));
    }

    @Test
    void le_maximum_du_match_est_respecte_quand_il_est_ecrit() throws Exception {
        UUID match = donnees().creerMatch(organisateur, "CONFIRMED");
        donnees().fixerMaxJoueurs(match, 14); // foot à 7

        ouvrir(organisateur, match, 13).andExpect(status().isOk()).andExpect(jsonPath("$.maxPlayers").value(14));
        ouvrir(organisateur, match, 14).andExpect(status().isBadRequest());
    }

    // Critère 2 : seul l'organisateur ouvre des places (403)

    @Test
    void une_autre_personne_recoit_403_sans_rien_changer() throws Exception {
        UUID match = donnees().creerMatch(organisateur, "CONFIRMED");

        ouvrir(UUID.randomUUID(), match, 3)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(PROBLEMES + "not-match-organizer"));

        assertThat(placesEnBase(match)).isZero();
        assertThat(donnees().nombreEvenements()).isZero();
    }

    @Test
    void match_annule_ou_joue_409_sans_evenement() throws Exception {
        for (String statut : List.of("CANCELLED", "PLAYED")) {
            UUID match = donnees().creerMatch(organisateur, statut);

            ouvrir(organisateur, match, 3)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.type").value(PROBLEMES + "match-closed"));

            assertThat(placesEnBase(match)).isZero();
        }
        assertThat(donnees().nombreEvenements()).isZero();
    }

    @Test
    void match_introuvable_404_et_sans_jeton_401() throws Exception {
        ouvrir(organisateur, UUID.randomUUID(), 3).andExpect(status().isNotFound());

        UUID match = donnees().creerMatch(organisateur, "CONFIRMED");
        mvc.perform(post("/api/v1/matches/" + match + "/open-spots")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"openSpots\":3}"))
                .andExpect(status().isUnauthorized());
        // L'identité vient du JWT : un en-tête X-User-Id ne suffit pas
        mvc.perform(post("/api/v1/matches/" + match + "/open-spots").header("X-User-Id", organisateur.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"openSpots\":3}"))
                .andExpect(status().isUnauthorized());
        assertThat(placesEnBase(match)).isZero();
    }
}
