package ma.teamslot.identity;

import static ma.teamslot.identity.AuthApiTests.PASSWORD;
import static ma.teamslot.identity.AuthApiTests.json;
import static ma.teamslot.identity.AuthApiTests.newPhone;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import ma.teamslot.identity.adapter.out.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * SCRUM-95 — critères d'acceptation : GET et PATCH /api/v1/users/me.
 * Appels HTTP réels sur un port aléatoire, MySQL et Redis (Testcontainers), comme AuthApiTests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ProfilApiTests {

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        RedisTestContainer.register(registry);
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    UserRepository users;

    final HttpClient http = HttpClient.newHttpClient();

    // ---------- contrôle d'accès ----------

    @Test
    void sansJeton_401_surLesDeuxRoutes() throws Exception {
        assertThat(appel("GET", "/api/v1/users/me", null, null).statusCode()).isEqualTo(401);
        assertThat(appel("PATCH", "/api/v1/users/me", null, "{\"displayName\":\"Karim\"}").statusCode()).isEqualTo(401);
    }

    @Test
    void jetonFalsifie_401() throws Exception {
        Compte compte = nouveauCompte();
        String[] parties = compte.jeton().split("\\.");
        // même en-tête et même signature, mais charge modifiée (autre sub) : la signature ne correspond plus
        String falsifie = parties[0] + "." + parties[1].substring(0, parties[1].length() - 4) + "AAAA." + parties[2];

        assertThat(appel("GET", "/api/v1/users/me", falsifie, null).statusCode()).isEqualTo(401);
        assertThat(appel("GET", "/api/v1/users/me", "pas-un-jeton", null).statusCode()).isEqualTo(401);
    }

    @Test
    void routesPubliquesToujoursOuvertes() throws Exception {
        assertThat(appel("GET", "/.well-known/jwks.json", null, null).statusCode()).isEqualTo(200);
        // l'inscription et la connexion restent sans jeton (nouveauCompte les utilise)
        assertThat(nouveauCompte().jeton()).isNotBlank();
    }

    // ---------- voir mon profil ----------

    @Test
    void monProfil_200_sansMotDePasse_etJamaisEnCache() throws Exception {
        Compte compte = nouveauCompte();

        HttpResponse<String> reponse = appel("GET", "/api/v1/users/me", compte.jeton(), null);

        assertThat(reponse.statusCode()).isEqualTo(200);
        Map<String, Object> profil = json(reponse);
        assertThat(profil).containsEntry("id", compte.id());
        assertThat(profil).containsEntry("phone", compte.phone());
        assertThat(profil).containsEntry("displayName", "Yassir");
        assertThat(profil).containsEntry("platformRole", "PLAYER");
        assertThat(reponse.body()).doesNotContain(PASSWORD).doesNotContainIgnoringCase("password").doesNotContain("$2");
        assertThat(reponse.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    }

    // ---------- modifier mon surnom ----------

    @Test
    void changerSurnom_200_espacesRetires_etEnregistre() throws Exception {
        Compte compte = nouveauCompte();

        HttpResponse<String> reponse = appel("PATCH", "/api/v1/users/me", compte.jeton(),
                "{\"displayName\":\"  Karim  \"}");

        assertThat(reponse.statusCode()).isEqualTo(200);
        assertThat(json(reponse)).containsEntry("displayName", "Karim");
        assertThat(reponse.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(json(appel("GET", "/api/v1/users/me", compte.jeton(), null))).containsEntry("displayName", "Karim");
        assertThat(users.findByPhone(compte.phone()).orElseThrow().getDisplayName()).isEqualTo("Karim");
    }

    @Test
    void surnomDe1et40caracteres_acceptes_videEspacesOu41_refuses() throws Exception {
        Compte compte = nouveauCompte();

        assertThat(patch(compte, "{\"displayName\":\"K\"}").statusCode()).isEqualTo(200);
        assertThat(patch(compte, "{\"displayName\":\"" + "a".repeat(40) + "\"}").statusCode()).isEqualTo(200);
        for (String refuse : new String[] {"\"\"", "\"    \"", "\"" + "a".repeat(41) + "\"", "null", "42",
                "\"Kar\\u0000im\""}) {
            HttpResponse<String> reponse = patch(compte, "{\"displayName\":" + refuse + "}");
            assertThat(reponse.statusCode()).as(refuse).isEqualTo(400);
            assertThat(json(reponse)).containsEntry("type", "https://teamslot.app/problems/invalid-profile");
        }
        assertThat(patch(compte, "{}").statusCode()).isEqualTo(400);
        assertThat(patch(compte, "pas du json").statusCode()).isEqualTo(400);
        assertThat(users.findByPhone(compte.phone()).orElseThrow().getDisplayName()).isEqualTo("a".repeat(40));
    }

    @Test
    void affectationDeMasse_champInconnu_400_etRienNestModifie() throws Exception {
        Compte compte = nouveauCompte();

        for (String champ : new String[] {"\"platformRole\":\"ADMIN\"", "\"managerStatus\":\"APPROVED\"",
                "\"phone\":\"+212611111111\"", "\"email\":\"pirate@exemple.ma\"", "\"id\":\"" + compte.id() + "\""}) {
            HttpResponse<String> reponse = patch(compte, "{\"displayName\":\"Pirate\"," + champ + "}");
            assertThat(reponse.statusCode()).as(champ).isEqualTo(400);
        }

        Map<String, Object> profil = json(appel("GET", "/api/v1/users/me", compte.jeton(), null));
        assertThat(profil).containsEntry("displayName", "Yassir");
        assertThat(profil).containsEntry("platformRole", "PLAYER");
        assertThat(profil).containsEntry("managerStatus", "NONE");
        assertThat(profil).containsEntry("phone", compte.phone());
    }

    @Test
    void chacunNeModifieQueSonProfil() throws Exception {
        Compte moi = nouveauCompte();
        Compte autre = nouveauCompte();

        patch(moi, "{\"displayName\":\"Karim\"}");

        assertThat(json(appel("GET", "/api/v1/users/me", autre.jeton(), null))).containsEntry("displayName", "Yassir");
        assertThat(json(appel("GET", "/api/v1/users/me", moi.jeton(), null))).containsEntry("id", moi.id());
    }

    // ---------- outils ----------

    record Compte(String id, String phone, String jeton) {
    }

    Compte nouveauCompte() throws Exception {
        String phone = newPhone();
        HttpResponse<String> inscription = appel("POST", "/api/v1/auth/register", null,
                "{\"phone\":\"" + phone + "\",\"password\":\"" + PASSWORD + "\",\"displayName\":\"Yassir\"}");
        assertThat(inscription.statusCode()).isEqualTo(201);
        HttpResponse<String> connexion = appel("POST", "/api/v1/auth/login", null,
                "{\"phone\":\"" + phone + "\",\"password\":\"" + PASSWORD + "\"}");
        assertThat(connexion.statusCode()).isEqualTo(200);
        return new Compte((String) json(inscription).get("id"), phone, (String) json(connexion).get("accessToken"));
    }

    HttpResponse<String> patch(Compte compte, String corps) throws Exception {
        return appel("PATCH", "/api/v1/users/me", compte.jeton(), corps);
    }

    HttpResponse<String> appel(String methode, String chemin, String jeton, String corps) throws Exception {
        HttpRequest.Builder requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin))
                .method(methode, corps == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(corps));
        if (corps != null) {
            requete.header("Content-Type", "application/json");
        }
        if (jeton != null) {
            requete.header("Authorization", "Bearer " + jeton);
        }
        return http.send(requete.build(), HttpResponse.BodyHandlers.ofString());
    }
}
