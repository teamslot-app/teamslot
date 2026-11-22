package ma.teamslot.identity;

import static ma.teamslot.identity.AuthApiTests.PASSWORD;
import static ma.teamslot.identity.AuthApiTests.json;
import static ma.teamslot.identity.AuthApiTests.newPhone;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * SCRUM-82 — critères d'acceptation : GET /api/v1/users?displayName=…
 * Appels HTTP réels sur un port aléatoire, MySQL et Redis (Testcontainers), comme ProfilApiTests.
 * Chaque test utilise un préfixe de surnom unique : les comptes des autres tests ne le gênent pas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RechercheJoueursApiTests {

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        RedisTestContainer.register(registry);
    }

    @Value("${local.server.port}")
    int port;

    final HttpClient http = HttpClient.newHttpClient();

    @Test
    void sansJeton_401() throws Exception {
        assertThat(chercher(null, "abc").statusCode()).isEqualTo(401);
    }

    @Test
    void debutDeSurnom_casseIgnoree_trie_sansMoi_etReponseMinimale() throws Exception {
        String p = prefixe();
        Compte moi = nouveauCompte(p + "moi");
        Compte b = nouveauCompte(p + "b");
        Compte a = nouveauCompte(p + "a");
        nouveauCompte("x" + p); // contient le texte sans commencer par lui : absent

        HttpResponse<String> reponse = chercher(moi.jeton(), p.toUpperCase(Locale.ROOT));

        assertThat(reponse.statusCode()).isEqualTo(200);
        List<Map<String, Object>> trouves = liste(reponse);
        assertThat(trouves.stream().map(t -> t.get("displayName")).toList()).containsExactly(p + "a", p + "b");
        assertThat(trouves.stream().map(t -> t.get("id")).toList()).containsExactly(a.id(), b.id());
        assertThat(trouves).allSatisfy(t -> assertThat(t.keySet()).containsExactlyInAnyOrder("id", "displayName"));
        assertThat(reponse.body()).doesNotContain(a.phone()).doesNotContain(b.phone())
                .doesNotContainIgnoringCase("phone").doesNotContainIgnoringCase("email");
        assertThat(reponse.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    }

    @Test
    void auPlus20resultats() throws Exception {
        String p = prefixe();
        Compte moi = nouveauCompte("moi" + p);
        for (int i = 10; i < 32; i++) {
            inscrire(p + i, newPhone());
        }

        assertThat(liste(chercher(moi.jeton(), p))).hasSize(20);
    }

    @Test
    void jokersSql_sontDeVraisCaracteres() throws Exception {
        Compte moi = nouveauCompte(prefixe());
        nouveauCompte(prefixe()); // des comptes existent : un % non échappé les renverrait tous

        for (String joker : new String[] {"%%%", "___", "%_%"}) {
            HttpResponse<String> reponse = chercher(moi.jeton(), joker);
            assertThat(reponse.statusCode()).as(joker).isEqualTo(200);
            assertThat(liste(reponse)).as(joker).isEmpty();
        }
    }

    @Test
    void texteManquantTropCourtOuTropLong_400() throws Exception {
        Compte moi = nouveauCompte(prefixe());

        for (String refuse : new String[] {"ab", "  ab  ", "", "a".repeat(41)}) {
            HttpResponse<String> reponse = chercher(moi.jeton(), refuse);
            assertThat(reponse.statusCode()).as("[" + refuse + "]").isEqualTo(400);
            assertThat(json(reponse)).containsEntry("type", "https://teamslot.app/problems/invalid-search");
        }
        assertThat(appel("/api/v1/users", moi.jeton()).statusCode()).isEqualTo(400);
    }

    @Test
    void limite30parMinute_429avecRetryAfter_parCompte() throws Exception {
        Compte moi = nouveauCompte(prefixe());
        Compte autre = nouveauCompte(prefixe());

        for (int i = 1; i <= 30; i++) {
            assertThat(chercher(moi.jeton(), "abc").statusCode()).as("recherche " + i).isEqualTo(200);
        }
        HttpResponse<String> refusee = chercher(moi.jeton(), "abc");

        assertThat(refusee.statusCode()).isEqualTo(429);
        assertThat(json(refusee)).containsEntry("type", "https://teamslot.app/problems/too-many-searches");
        long attente = Long.parseLong(refusee.headers().firstValue("Retry-After").orElse("0"));
        assertThat(attente).isBetween(1L, 60L);
        assertThat(chercher(autre.jeton(), "abc").statusCode()).isEqualTo(200); // la limite est par compte
    }

    // ---------- outils ----------

    record Compte(String id, String phone, String jeton) {
    }

    /** Préfixe unique de 13 caractères (lettres minuscules et chiffres). */
    static String prefixe() {
        return "r" + Long.toString(System.nanoTime(), 36);
    }

    /** Inscription seule, sans connexion : pour les comptes qu'on cherche sans jamais s'en servir. */
    HttpResponse<String> inscrire(String surnom, String phone) throws Exception {
        HttpResponse<String> inscription = post("/api/v1/auth/register",
                "{\"phone\":\"" + phone + "\",\"password\":\"" + PASSWORD + "\",\"displayName\":\"" + surnom + "\"}");
        assertThat(inscription.statusCode()).isEqualTo(201);
        return inscription;
    }

    Compte nouveauCompte(String surnom) throws Exception {
        String phone = newPhone();
        HttpResponse<String> inscription = inscrire(surnom, phone);
        HttpResponse<String> connexion = post("/api/v1/auth/login",
                "{\"phone\":\"" + phone + "\",\"password\":\"" + PASSWORD + "\"}");
        assertThat(connexion.statusCode()).isEqualTo(200);
        return new Compte((String) json(inscription).get("id"), phone, (String) json(connexion).get("accessToken"));
    }

    HttpResponse<String> chercher(String jeton, String texte) throws Exception {
        return appel("/api/v1/users?displayName=" + URLEncoder.encode(texte, StandardCharsets.UTF_8), jeton);
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> liste(HttpResponse<String> reponse) {
        return JsonParserFactory.getJsonParser().parseList(reponse.body()).stream()
                .map(element -> (Map<String, Object>) element)
                .toList();
    }

    HttpResponse<String> appel(String chemin, String jeton) throws Exception {
        HttpRequest.Builder requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin)).GET();
        if (jeton != null) {
            requete.header("Authorization", "Bearer " + jeton);
        }
        return http.send(requete.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> post(String chemin, String corps) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corps))
                .build(), HttpResponse.BodyHandlers.ofString());
    }
}
