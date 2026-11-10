package ma.teamslot.match;

import static ma.teamslot.match.MatchCreationTests.appelant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** SCRUM-118 : GET /api/v1/matches/open?date=, un test par critère d'acceptation. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MongoDeTest.class)
class OpenMatchesTests {

    private static final ZoneId MARRAKECH = ZoneId.of("Africa/Casablanca");

    @Autowired MockMvc mvc;
    @Autowired MongoTemplate mongo;

    private UUID organisateur;
    /** Un jour à venir : aucun match de ce jour n'a commencé, quelle que soit l'heure du test. */
    private LocalDate jour;

    @BeforeEach
    void preparer() {
        donnees().vider();
        organisateur = UUID.randomUUID();
        jour = LocalDate.now(MARRAKECH).plusDays(3);
    }

    private DonneesMongo donnees() {
        return new DonneesMongo(mongo);
    }

    private Instant a(LocalDate date, int heure, int minute) {
        return date.atTime(LocalTime.of(heure, minute)).atZone(MARRAKECH).toInstant();
    }

    private UUID match(Instant debut, int places) {
        return donnees().creerMatch(organisateur, "CONFIRMED", debut, places);
    }

    private ResultActions ouverts(LocalDate date) throws Exception {
        return mvc.perform(get("/api/v1/matches/open").param("date", date.toString()).with(appelant(UUID.randomUUID())));
    }

    // Critère : seulement les matchs avec des places, pas commencés, du jour demandé, par heure

    @Test
    void liste_les_matchs_du_jour_avec_des_places_par_heure_de_debut() throws Exception {
        UUID soir = match(a(jour, 20, 0), 2);
        UUID matin = donnees().creerMatch(organisateur, "PENDING_PAYMENT", a(jour, 10, 0), 1);
        match(a(jour, 12, 0), 0); // aucune place
        donnees().creerMatch(organisateur, "CANCELLED", a(jour, 14, 0), 3); // annulé
        donnees().creerMatch(organisateur, "PLAYED", a(jour, 15, 0), 3); // joué
        match(a(jour.plusDays(1), 18, 0), 2); // autre jour

        ouverts(jour)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(matin.toString()))
                .andExpect(jsonPath("$[0].openSpots").value(1))
                .andExpect(jsonPath("$[0].maxPlayers").value(10))
                .andExpect(jsonPath("$[1].id").value(soir.toString()))
                .andExpect(jsonPath("$[1].openSpots").value(2))
                .andExpect(jsonPath("$[1].startsAt").exists())
                .andExpect(jsonPath("$[1].venueId").exists());
    }

    @Test
    void aucune_donnee_des_participants_ni_de_l_organisateur() throws Exception {
        UUID ouvert = match(a(jour, 20, 0), 2);
        donnees().ajouterParticipant(ouvert, null, "Yassir", "CONFIRMED", organisateur);
        donnees().ajouterParticipant(ouvert, UUID.randomUUID(), null, "INVITED", organisateur);

        String corps = ouverts(jour).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(corps).doesNotContain("Yassir", organisateur.toString(), "participants", "ownerId", "payerId",
                "phone", "guestName", "userId");
    }

    @Test
    void un_match_deja_commence_n_apparait_pas() throws Exception {
        Instant ilYaUneHeure = Instant.now().minus(1, ChronoUnit.HOURS);
        UUID commence = match(ilYaUneHeure, 2);

        String corps = ouverts(LocalDate.ofInstant(ilYaUneHeure, MARRAKECH))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(corps).doesNotContain(commence.toString());
        ouverts(LocalDate.of(2020, 1, 1)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void le_jour_est_compte_a_marrakech() throws Exception {
        UUID tard = match(a(jour, 23, 30), 1);
        UUID apresMinuit = match(a(jour.plusDays(1), 0, 10), 1);

        ouverts(jour).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(tard.toString()));
        ouverts(jour.plusDays(1)).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(apresMinuit.toString()));
    }

    @Test
    void cinquante_matchs_au_plus() throws Exception {
        for (int i = 0; i < 51; i++) {
            match(a(jour, 8, 0).plus(i, ChronoUnit.MINUTES), 1);
        }

        ouverts(jour).andExpect(jsonPath("$.length()").value(50));
    }

    @Test
    void date_absente_ou_invalide_400_et_sans_jeton_401() throws Exception {
        mvc.perform(get("/api/v1/matches/open").with(appelant(organisateur))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/matches/open").param("date", "14/11/2026").with(appelant(organisateur)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/matches/open").param("date", "2026-13-40").with(appelant(organisateur)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/matches/open").param("date", jour.toString())).andExpect(status().isUnauthorized());
    }

    @Test
    void index_partiel_des_matchs_ouverts_cree_au_demarrage() {
        assertThat(mongo.indexOps(DonneesMongo.MATCHES).getIndexInfo())
                .extracting(IndexInfo::getName)
                .contains("idx_matches_places_ouvertes");
    }
}
