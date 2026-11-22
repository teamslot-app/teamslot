package ma.teamslot.match;

import static ma.teamslot.match.MatchCreationTests.appelant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
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

/** SCRUM-77 : GET /api/v1/matches et GET /api/v1/matches/{id}, un test par critère d'acceptation. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MongoDeTest.class)
class MyMatchesTests {

    @Autowired MockMvc mvc;
    @Autowired MongoTemplate mongo;

    private UUID moi;
    private UUID autre;

    @BeforeEach
    void preparer() {
        donnees().vider();
        moi = UUID.randomUUID();
        autre = UUID.randomUUID();
    }

    private DonneesMongo donnees() {
        return new DonneesMongo(mongo);
    }

    private static Instant dansJours(int jours) {
        return Instant.now().plus(jours, ChronoUnit.DAYS);
    }

    private UUID matchDe(UUID organisateur, String statut, Instant debut) {
        return donnees().creerMatch(organisateur, statut, debut, 0);
    }

    private ResultActions mesMatchs(UUID qui) throws Exception {
        return mvc.perform(get("/api/v1/matches").with(appelant(qui)));
    }

    private ResultActions detail(UUID matchId, UUID qui) throws Exception {
        return mvc.perform(get("/api/v1/matches/" + matchId).with(appelant(qui)));
    }

    // Critère : date, heure, terrain, statut ; organisateur et participant ; par heure de début

    @Test
    void mes_matchs_organisateur_et_participant_par_heure_de_debut() throws Exception {
        UUID organise = matchDe(moi, "CONFIRMED", dansJours(2));
        UUID invite = matchDe(autre, "PENDING_PAYMENT", dansJours(1));
        donnees().ajouterParticipant(invite, moi, null, "INVITED", autre);
        UUID confirme = matchDe(autre, "CONFIRMED", dansJours(3));
        donnees().ajouterParticipant(confirme, moi, null, "CONFIRMED", autre);

        mesMatchs(moi)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(invite.toString()))
                .andExpect(jsonPath("$[1].id").value(organise.toString()))
                .andExpect(jsonPath("$[2].id").value(confirme.toString()))
                .andExpect(jsonPath("$[1].status").value("CONFIRMED"))
                .andExpect(jsonPath("$[1].startsAt").exists())
                .andExpect(jsonPath("$[1].venueId").exists());
    }

    // Critère S1 : jamais les matchs des autres

    @Test
    void jamais_les_matchs_des_autres_ni_une_invitation_refusee_ni_une_simple_demande() throws Exception {
        UUID mien = matchDe(moi, "CONFIRMED", dansJours(1));
        matchDe(autre, "CONFIRMED", dansJours(1));
        UUID refuse = matchDe(autre, "CONFIRMED", dansJours(2));
        donnees().ajouterParticipant(refuse, moi, null, "DECLINED", autre);
        UUID demande = matchDe(autre, "CONFIRMED", dansJours(3));
        donnees().ajouterDemande(demande, moi);
        // Mon compte dans un participant DECLINED et un autre participant CONFIRMED : $elemMatch exige le même
        UUID melange = matchDe(autre, "CONFIRMED", dansJours(4));
        donnees().ajouterParticipant(melange, moi, null, "DECLINED", autre);
        donnees().ajouterParticipant(melange, UUID.randomUUID(), null, "CONFIRMED", autre);

        mesMatchs(moi)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mien.toString()));
        mesMatchs(autre).andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void aucune_donnee_des_autres_joueurs_dans_la_reponse() throws Exception {
        UUID mien = matchDe(moi, "CONFIRMED", dansJours(1));
        UUID compteDUnAmi = UUID.randomUUID();
        donnees().ajouterParticipant(mien, null, "Yassir", "CONFIRMED", moi);
        donnees().ajouterParticipant(mien, compteDUnAmi, null, "INVITED", moi);
        donnees().ajouterDemande(mien, UUID.randomUUID());

        String liste = mesMatchs(moi).andReturn().getResponse().getContentAsString();
        String unSeul = detail(mien, moi).andReturn().getResponse().getContentAsString();

        assertThat(liste).contains(mien.toString())
                .doesNotContain("Yassir", compteDUnAmi.toString(), "participants", "joinRequests", "guestName");
        assertThat(unSeul).contains(mien.toString())
                .doesNotContain("Yassir", compteDUnAmi.toString(), "participants", "joinRequests", "guestName");
    }

    // Critère : les matchs annulés sont affichés

    @Test
    void un_match_annule_reste_affiche_avec_son_statut() throws Exception {
        UUID annule = matchDe(moi, "CANCELLED", dansJours(1));

        mesMatchs(moi)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(annule.toString()))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"));
    }

    // « À venir » : jusqu'à la fin du créneau d'une heure

    @Test
    void un_match_en_cours_reste_et_un_creneau_termine_disparait() throws Exception {
        UUID enCours = matchDe(moi, "CONFIRMED", Instant.now().minus(30, ChronoUnit.MINUTES));
        UUID termine = matchDe(moi, "CONFIRMED", Instant.now().minus(2, ChronoUnit.HOURS));

        String corps = mesMatchs(moi).andReturn().getResponse().getContentAsString();

        assertThat(corps).contains(enCours.toString()).doesNotContain(termine.toString());
    }

    @Test
    void cinquante_matchs_au_plus_et_liste_vide_sans_match() throws Exception {
        mesMatchs(moi).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        for (int i = 0; i < 51; i++) {
            matchDe(moi, "CONFIRMED", dansJours(1).plus(i, ChronoUnit.MINUTES));
        }

        mesMatchs(moi).andExpect(jsonPath("$.length()").value(50));
    }

    // Détail d'un match : 404 pour les autres (même réponse qu'un match inexistant)

    @Test
    void detail_pour_l_organisateur_et_un_participant_confirme() throws Exception {
        UUID match = matchDe(autre, "CONFIRMED", dansJours(1));
        donnees().ajouterParticipant(match, moi, null, "CONFIRMED", autre);

        detail(match, autre).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(match.toString()));
        detail(match, moi)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.ownerId").value(autre.toString()))
                .andExpect(jsonPath("$.totalPrice.currency").value("MAD"));
    }

    @Test
    void detail_404_pour_un_autre_joueur_comme_pour_un_match_inexistant() throws Exception {
        UUID match = matchDe(autre, "CONFIRMED", dansJours(1));
        UUID refuse = matchDe(autre, "CONFIRMED", dansJours(2));
        donnees().ajouterParticipant(refuse, moi, null, "DECLINED", autre);

        // Même type, même titre, même message : rien ne distingue « pas à toi » de « n'existe pas »
        for (UUID id : new UUID[] {match, refuse, UUID.randomUUID()}) {
            String corps = detail(id, moi)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.type").value("https://teamslot.app/problems/match-not-found"))
                    .andExpect(jsonPath("$.title").value("Match introuvable"))
                    .andExpect(jsonPath("$.detail").value("Ce match n'existe pas."))
                    .andReturn().getResponse().getContentAsString();
            assertThat(corps).doesNotContain(autre.toString());
        }
    }

    // Contrôle d'accès : 401 sans jeton, 400 pour un identifiant invalide, /open inchangé

    @Test
    void sans_jeton_401() throws Exception {
        UUID match = matchDe(moi, "CONFIRMED", dansJours(1));

        mvc.perform(get("/api/v1/matches")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/matches/" + match)).andExpect(status().isUnauthorized());
    }

    @Test
    void identifiant_invalide_400_et_matchs_ouverts_toujours_servis() throws Exception {
        mvc.perform(get("/api/v1/matches/pas-un-uuid").with(appelant(moi))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/matches/open")
                        .param("date", LocalDate.now(ZoneId.of("Africa/Casablanca")).plusDays(1).toString())
                        .with(appelant(moi)))
                .andExpect(status().isOk());
    }
}
