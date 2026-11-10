package ma.teamslot.match;

import static ma.teamslot.match.MatchCreationTests.appelant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** SCRUM-79 : demander à rejoindre, voir et accepter les demandes ; un test par critère d'acceptation. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MongoDeTest.class)
class JoinMatchTests {

    private static final String PROBLEMES = "https://teamslot.app/problems/";

    @Autowired MockMvc mvc;
    @Autowired MongoTemplate mongo;

    private UUID organisateur;
    private UUID joueur;

    @BeforeEach
    void preparer() {
        donnees().vider();
        organisateur = UUID.randomUUID();
        joueur = UUID.randomUUID();
    }

    private DonneesMongo donnees() {
        return new DonneesMongo(mongo);
    }

    private UUID matchAvecPlaces(int places) {
        return donnees().creerMatch(organisateur, "CONFIRMED", Instant.now().plus(3, ChronoUnit.DAYS), places);
    }

    private ResultActions demander(UUID qui, UUID match) throws Exception {
        return mvc.perform(post("/api/v1/matches/" + match + "/join").with(appelant(qui)));
    }

    private ResultActions accepter(UUID qui, UUID match, UUID demandeur) throws Exception {
        return mvc.perform(post("/api/v1/matches/" + match + "/join-requests/" + demandeur + "/accept")
                .with(appelant(qui)));
    }

    private int places(UUID match) {
        return ((Number) donnees().match(match).get("openSpots")).intValue();
    }

    private List<Document> demandesEnBase(UUID match) {
        List<Document> demandes = donnees().match(match).getList("joinRequests", Document.class);
        return demandes == null ? List.of() : demandes;
    }

    private List<String> types(UUID match) {
        return donnees().evenementsDe(match).stream().map(e -> e.getString("eventType")).toList();
    }

    // Critère 1 : demande -> match.join_requested ; acceptation -> match.join_accepted

    @Test
    void un_joueur_demande_une_place_et_publie_match_join_requested() throws Exception {
        UUID match = matchAvecPlaces(2);

        demander(joueur, match)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.matchId").value(match.toString()))
                .andExpect(jsonPath("$.userId").value(joueur.toString()))
                .andExpect(jsonPath("$.requestedAt").exists());

        assertThat(demandesEnBase(match)).singleElement()
                .satisfies(d -> assertThat(d.getString("userId")).isEqualTo(joueur.toString()));
        assertThat(places(match)).isEqualTo(2); // une demande ne prend pas de place
        assertThat(types(match)).containsExactly("match.join_requested");
        JsonNode data = DonneesMongo.payload(donnees().evenementsDe(match).getFirst()).get("data");
        assertThat(data.get("matchId").asString()).isEqualTo(match.toString());
        assertThat(data.get("userId").asString()).isEqualTo(joueur.toString());
    }

    @Test
    void l_organisateur_voit_les_demandes_puis_accepte_le_joueur_devient_participant() throws Exception {
        UUID match = matchAvecPlaces(2);
        demander(joueur, match).andExpect(status().isAccepted());

        mvc.perform(get("/api/v1/matches/" + match + "/join-requests").with(appelant(organisateur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(joueur.toString()));

        accepter(organisateur, match, joueur)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(joueur.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.guestName").doesNotExist());

        assertThat(places(match)).isEqualTo(1);
        assertThat(demandesEnBase(match)).isEmpty();
        assertThat(donnees().nombreParticipants(p -> joueur.toString().equals(p.getString("userId"))
                && "CONFIRMED".equals(p.getString("status")))).isEqualTo(1);
        assertThat(types(match)).containsExactly("match.join_requested", "match.join_accepted");
        // Le joueur accepté voit maintenant la liste des participants (SCRUM-61)
        mvc.perform(get("/api/v1/matches/" + match + "/participants").with(appelant(joueur)))
                .andExpect(status().isOk());
    }

    // Critère 2 : match complet -> 409, même en appelant l'endpoint directement (S9)

    @Test
    void match_sans_place_409_pour_la_demande_et_pour_l_acceptation() throws Exception {
        UUID complet = matchAvecPlaces(0);
        demander(joueur, complet)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMES + "no-open-spots"));
        assertThat(demandesEnBase(complet)).isEmpty();

        // La demande date d'avant : les places ont été prises entre-temps
        donnees().ajouterDemande(complet, joueur);
        accepter(organisateur, complet, joueur)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMES + "no-open-spots"));
        assertThat(donnees().nombreParticipants()).isZero();
        assertThat(donnees().nombreEvenements()).isZero();
    }

    @Test
    void deux_acceptations_en_meme_temps_pour_une_seule_place_un_seul_joueur_entre() throws Exception {
        UUID match = matchAvecPlaces(1);
        UUID autre = UUID.randomUUID();
        donnees().ajouterDemande(match, joueur);
        donnees().ajouterDemande(match, autre);

        CountDownLatch depart = new CountDownLatch(1);
        ExecutorService fils = Executors.newFixedThreadPool(2);
        List<Future<Integer>> codes = new ArrayList<>();
        for (UUID demandeur : List.of(joueur, autre)) {
            Callable<Integer> acceptation = () -> {
                depart.await();
                return accepter(organisateur, match, demandeur).andReturn().getResponse().getStatus();
            };
            codes.add(fils.submit(acceptation));
        }
        depart.countDown();
        List<Integer> statuts = new ArrayList<>();
        for (Future<Integer> code : codes) {
            statuts.add(code.get());
        }
        fils.shutdown();

        assertThat(statuts).containsExactlyInAnyOrder(200, 409);
        assertThat(places(match)).isZero();
        assertThat(donnees().nombreParticipants()).isEqualTo(1);
        assertThat(types(match)).containsExactly("match.join_accepted");
    }

    // Critère 3 : pas deux demandes du même joueur (409) ; ni l'organisateur ni un participant ne demandent

    @Test
    void double_demande_409_et_une_seule_en_base() throws Exception {
        UUID match = matchAvecPlaces(2);

        demander(joueur, match).andExpect(status().isAccepted());
        demander(joueur, match)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMES + "join-already-requested"));

        assertThat(demandesEnBase(match)).hasSize(1);
        assertThat(types(match)).containsExactly("match.join_requested");
    }

    @Test
    void l_organisateur_ou_un_participant_ne_peut_pas_demander() throws Exception {
        UUID match = matchAvecPlaces(2);
        donnees().ajouterParticipant(match, joueur, null, "INVITED", organisateur);

        demander(organisateur, match)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMES + "participant-already-added"));
        demander(joueur, match)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMES + "participant-already-added"));
        assertThat(demandesEnBase(match)).isEmpty();
    }

    @Test
    void match_annule_ou_joue_409_match_closed() throws Exception {
        for (String statut : List.of("CANCELLED", "PLAYED")) {
            UUID match = donnees().creerMatch(organisateur, statut, Instant.now().plus(1, ChronoUnit.DAYS), 2);
            demander(joueur, match)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.type").value(PROBLEMES + "match-closed"));
        }
        assertThat(donnees().nombreEvenements()).isZero();
    }

    // Critère 4 : seul l'organisateur accepte (403) et voit les demandes (403)

    @Test
    void un_non_organisateur_ne_voit_pas_les_demandes_et_n_accepte_pas() throws Exception {
        UUID match = matchAvecPlaces(2);
        donnees().ajouterDemande(match, joueur);

        mvc.perform(get("/api/v1/matches/" + match + "/join-requests").with(appelant(joueur)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(PROBLEMES + "not-match-organizer"));
        accepter(joueur, match, joueur)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(PROBLEMES + "not-match-organizer"));
        accepter(UUID.randomUUID(), match, joueur).andExpect(status().isForbidden());

        assertThat(places(match)).isEqualTo(2);
        assertThat(demandesEnBase(match)).hasSize(1);
        assertThat(donnees().nombreParticipants()).isZero();
        assertThat(donnees().nombreEvenements()).isZero();
    }

    @Test
    void accepter_sans_demande_404_et_deux_fois_404() throws Exception {
        UUID match = matchAvecPlaces(2);

        accepter(organisateur, match, joueur)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(PROBLEMES + "join-request-not-found"));

        donnees().ajouterDemande(match, joueur);
        accepter(organisateur, match, joueur).andExpect(status().isOk());
        accepter(organisateur, match, joueur).andExpect(status().isNotFound());
        assertThat(places(match)).isEqualTo(1);
    }

    @Test
    void match_introuvable_404_et_sans_jeton_401() throws Exception {
        UUID inconnu = UUID.randomUUID();
        demander(joueur, inconnu).andExpect(status().isNotFound());
        accepter(organisateur, inconnu, joueur).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/matches/" + inconnu + "/join-requests").with(appelant(organisateur)))
                .andExpect(status().isNotFound());

        UUID match = matchAvecPlaces(2);
        mvc.perform(post("/api/v1/matches/" + match + "/join")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/matches/" + match + "/join").header("X-User-Id", joueur.toString()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/matches/" + match + "/join-requests")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/matches/" + match + "/join-requests/" + joueur + "/accept"))
                .andExpect(status().isUnauthorized());
        assertThat(demandesEnBase(match)).isEmpty();
    }

    @Test
    void le_joueur_accepte_compte_dans_les_places_du_match() throws Exception {
        // 10 joueurs au plus : organisateur seul -> 9 places ; un joueur accepté -> 2 joueurs, 8 places au plus
        UUID match = matchAvecPlaces(0);
        mvc.perform(post("/api/v1/matches/" + match + "/open-spots").with(appelant(organisateur))
                        .contentType("application/json").content("{\"openSpots\":9}"))
                .andExpect(status().isOk());
        donnees().ajouterDemande(match, joueur);
        accepter(organisateur, match, joueur).andExpect(status().isOk());

        mvc.perform(post("/api/v1/matches/" + match + "/open-spots").with(appelant(organisateur))
                        .contentType("application/json").content("{\"openSpots\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maxOpenSpots").value(8));
    }

    @Test
    void validateur_de_la_base_refuse_une_demande_sans_date() {
        UUID match = matchAvecPlaces(1);
        assertThatThrownBy(() -> mongo.updateFirst(Query.query(Criteria.where("_id").is(match.toString())),
                        new Update().push("joinRequests", new Document("userId", joueur.toString())),
                        DonneesMongo.MATCHES))
                .isInstanceOf(RuntimeException.class);
    }
}
