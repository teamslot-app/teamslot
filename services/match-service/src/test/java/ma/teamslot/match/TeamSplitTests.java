package ma.teamslot.match;

import static ma.teamslot.match.MatchCreationTests.appelant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.function.Predicate;
import org.bson.Document;
import org.hamcrest.Matchers;
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

/** SCRUM-61 : POST /matches/{id}/split et GET /matches/{id}/participants, un test par règle. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MongoDeTest.class)
class TeamSplitTests {

    @Autowired MockMvc mvc;
    @Autowired MongoTemplate mongo;

    private UUID organisateur;
    private UUID matchId;

    @BeforeEach
    void preparer() {
        donnees().vider();
        organisateur = UUID.randomUUID();
        matchId = creerMatch("CONFIRMED");
    }

    private DonneesMongo donnees() {
        return new DonneesMongo(mongo);
    }

    private UUID creerMatch(String statut) {
        return donnees().creerMatch(organisateur, statut);
    }

    private void inserer(UUID match, UUID compte, String surnom, String statut) {
        donnees().ajouterParticipant(match, compte, surnom, statut, organisateur);
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

    /** Nombre de participants (tous matchs confondus) qui vérifient la condition. */
    private long participants(Predicate<Document> condition) {
        return donnees().nombreParticipants(condition);
    }

    private static Predicate<Document> tous() {
        return p -> true;
    }

    private static Predicate<Document> avecCote() {
        return p -> p.get("side") != null;
    }

    private static Predicate<Document> sansCote() {
        return p -> p.get("side") == null;
    }

    private static Predicate<Document> compte(UUID compte) {
        return p -> compte.toString().equals(p.getString("userId"));
    }

    private long evenementsTeamsSplit() {
        return donnees().evenements().stream().filter(e -> "match.teams_split".equals(e.getString("eventType"))).count();
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
        assertThat(participants(avecCote())).isEqualTo(4);
        assertThat(participants(p -> "A".equals(p.get("side")))).isEqualTo(2);
        assertThat(participants(compte(demande).and(sansCote()))).isEqualTo(1);
        assertThat(participants(compte(organisateur).and(p -> "CONFIRMED".equals(p.get("status"))))).isEqualTo(1);

        assertThat(evenementsTeamsSplit()).isEqualTo(1);
        assertThat(donnees().evenements()).hasSize(1);
        var data = DonneesMongo.payload(donnees().evenements().getFirst()).get("data");
        assertThat(data.get("strategy").asString()).isEqualTo("random");
        assertThat(data.toString()).contains("guest:Yassir").contains("user:" + organisateur)
                .doesNotContain(demande.toString());
    }

    @Test
    void un_nouveau_tirage_remplace_les_cotes_sans_dedoubler_l_organisateur() throws Exception {
        ajouterCompte(matchId, "INVITED");
        ajouterCompte(matchId, "INVITED");
        repartir(organisateur, matchId, "random").andExpect(status().isOk());

        ajouterCompte(matchId, "CONFIRMED"); // ami ajouté après le premier tirage
        assertThat(participants(sansCote())).isEqualTo(1);

        repartir(organisateur, matchId, "random")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sideA.length()").value(2))
                .andExpect(jsonPath("$.sideB.length()").value(2));

        assertThat(participants(avecCote())).isEqualTo(4);
        assertThat(participants(compte(organisateur))).isEqualTo(1);
        assertThat(evenementsTeamsSplit()).isEqualTo(2);
    }

    @Test
    void un_autre_que_l_organisateur_403_sans_rien_changer() throws Exception {
        ajouterCompte(matchId, "CONFIRMED");

        repartir(UUID.randomUUID(), matchId, "random").andExpect(status().isForbidden());

        assertThat(participants(tous())).isEqualTo(1);
        assertThat(participants(avecCote())).isZero();
        assertThat(evenementsTeamsSplit()).isZero();
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

        assertThat(participants(tous())).isEqualTo(2);
        assertThat(participants(avecCote())).isZero();
        assertThat(evenementsTeamsSplit()).isZero();
    }

    @Test
    void organisateur_seul_409_et_son_inscription_est_annulee() throws Exception {
        repartir(organisateur, matchId, "random").andExpect(status().isConflict());

        assertThat(participants(tous())).isZero();
        assertThat(evenementsTeamsSplit()).isZero();
    }

    @Test
    void strategie_inconnue_ou_absente_400_sans_rien_changer() throws Exception {
        ajouterCompte(matchId, "CONFIRMED");

        repartir(organisateur, matchId, "balanced").andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/matches/" + matchId + "/split").with(appelant(organisateur)))
                .andExpect(status().isBadRequest());

        assertThat(participants(tous())).isEqualTo(1);
        assertThat(evenementsTeamsSplit()).isZero();
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
