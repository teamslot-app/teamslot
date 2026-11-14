package ma.teamslot.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import com.nimbusds.jose.util.JSONObjectUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * SCRUM-133 — vérification de l'e-mail par code à 6 chiffres. Appels HTTP réels, MySQL et Redis (Testcontainers).
 * Le code est lu dans l'outbox (événement identity.email_verification_requested), comme notification-service
 * le recevra : il n'existe nulle part ailleurs en clair.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class VerificationEmailApiTests {

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        RedisTestContainer.register(registry);
    }

    static final String PASSWORD = "MotDePasse-Solide-42";

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcClient jdbc;

    final HttpClient http = HttpClient.newHttpClient();

    // ---------- inscription ----------

    @Test
    void inscription_publieUserRegisteredPuisLeCode_avecLeCorrelationId() throws Exception {
        Compte compte = nouveauCompte();
        String correlationId = UUID.randomUUID().toString();

        HttpResponse<String> reponse = http.send(HttpRequest.newBuilder(uri("/api/v1/auth/register"))
                .header("Content-Type", "application/json")
                .header("X-Correlation-Id", correlationId)
                .POST(HttpRequest.BodyPublishers.ofString(compte.json())).build(), HttpResponse.BodyHandlers.ofString());

        assertThat(reponse.statusCode()).isEqualTo(201);
        String userId = (String) json(reponse).get("id");
        assertThat(jdbc.sql("""
                SELECT event_type FROM outbox_event WHERE aggregate_id = :id ORDER BY position
                """).param("id", userId).query(String.class).list())
                .containsExactly("user.registered", "identity.email_verification_requested");
        assertThat(jdbc.sql("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(payload, '$.correlationId')) FROM outbox_event
                WHERE aggregate_id = :id AND event_type = 'identity.email_verification_requested'
                """).param("id", userId).query(String.class).single()).isEqualTo(correlationId);
        assertThat(codeEnvoye(compte.email)).matches("^[0-9]{6}$");
    }

    @Test
    void leCodeNestJamaisDansLaReponseNiEnClairEnBase() throws Exception {
        Compte compte = nouveauCompte();
        HttpResponse<String> reponse = inscrire(compte);
        String code = codeEnvoye(compte.email);

        assertThat(reponse.body()).doesNotContain("\"" + code + "\"");
        String empreinte = jdbc.sql("SELECT code_hash FROM email_verification WHERE user_id = UUID_TO_BIN(:id)")
                .param("id", json(reponse).get("id")).query(String.class).single();
        assertThat(empreinte).startsWith("$2").doesNotContain(code);
    }

    // ---------- connexion ----------

    @Test
    void connexion_refuseeAvantVerification_puisAccepteeApres() throws Exception {
        Compte compte = nouveauCompte();
        inscrire(compte);

        HttpResponse<String> avant = connexion(compte.phone, PASSWORD);
        assertThat(avant.statusCode()).isEqualTo(403);
        assertThat(json(avant).get("type")).isEqualTo("https://teamslot.app/problems/email-not-verified");

        assertThat(verifier(compte.email, codeEnvoye(compte.email)).statusCode()).isEqualTo(204);
        assertThat(connexion(compte.phone, PASSWORD).statusCode()).isEqualTo(200);
    }

    @Test
    void mauvaisMotDePasse_401_etJamais403() throws Exception {
        // 403 seulement après un mot de passe correct : un inconnu n'apprend pas qu'un compte attend sa vérification
        Compte compte = nouveauCompte();
        inscrire(compte);
        assertThat(connexion(compte.phone, "Mauvais-Mot-De-Passe-1").statusCode()).isEqualTo(401);
    }

    // ---------- vérification ----------

    @Test
    void mauvaisCode_400_puisSixiemeEssai_429_memeAvecLeBonCode() throws Exception {
        Compte compte = nouveauCompte();
        inscrire(compte);
        String bon = codeEnvoye(compte.email);
        String faux = bon.equals("000000") ? "111111" : "000000";

        for (int essai = 1; essai <= 5; essai++) {
            HttpResponse<String> reponse = verifier(compte.email, faux);
            assertThat(reponse.statusCode()).isEqualTo(400);
            assertThat(json(reponse).get("type")).isEqualTo("https://teamslot.app/problems/invalid-code");
        }
        HttpResponse<String> sixieme = verifier(compte.email, bon);
        assertThat(sixieme.statusCode()).isEqualTo(429);
        assertThat(json(sixieme).get("type")).isEqualTo("https://teamslot.app/problems/too-many-attempts");
    }

    @Test
    void codeExpire_410() throws Exception {
        Compte compte = nouveauCompte();
        String userId = (String) json(inscrire(compte)).get("id");
        jdbc.sql("UPDATE email_verification SET expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 MINUTE WHERE user_id = UUID_TO_BIN(:id)")
                .param("id", userId).update();

        HttpResponse<String> reponse = verifier(compte.email, codeEnvoye(compte.email));

        assertThat(reponse.statusCode()).isEqualTo(410);
        assertThat(json(reponse).get("type")).isEqualTo("https://teamslot.app/problems/code-expired");
    }

    @Test
    void codeAUsageUnique() throws Exception {
        Compte compte = nouveauCompte();
        inscrire(compte);
        String code = codeEnvoye(compte.email);

        assertThat(verifier(compte.email, code).statusCode()).isEqualTo(204);
        assertThat(verifier(compte.email, code).statusCode()).isEqualTo(400);
    }

    @Test
    void emailInconnu_memeReponseQuUnMauvaisCode() throws Exception {
        HttpResponse<String> reponse = verifier("personne-" + UUID.randomUUID() + "@exemple.ma", "123456");

        assertThat(reponse.statusCode()).isEqualTo(400);
        assertThat(json(reponse).get("type")).isEqualTo("https://teamslot.app/problems/invalid-code");
    }

    @Test
    void codeMalForme_400() throws Exception {
        assertThat(verifier("a@exemple.ma", "12ab56").statusCode()).isEqualTo(400);
    }

    // ---------- renvoi ----------

    @Test
    void renvoi_202_nouveauCode_lAncienNeMarchePlus() throws Exception {
        Compte compte = nouveauCompte();
        inscrire(compte);
        String ancien = codeEnvoye(compte.email);

        assertThat(renvoyer(compte.email).statusCode()).isEqualTo(202);
        assertThat(nombreDeCodesEnvoyes(compte.email)).isEqualTo(2);
        String nouveau = codeEnvoye(compte.email);

        if (!nouveau.equals(ancien)) {
            assertThat(verifier(compte.email, ancien).statusCode()).isEqualTo(400);
        }
        assertThat(verifier(compte.email, nouveau).statusCode()).isEqualTo(204);
    }

    @Test
    void renvoiTropRapide_toujours202_maisAucunNouveauCode() throws Exception {
        Compte compte = nouveauCompte();
        inscrire(compte);

        assertThat(renvoyer(compte.email).statusCode()).isEqualTo(202);
        assertThat(renvoyer(compte.email).statusCode()).isEqualTo(202);

        assertThat(nombreDeCodesEnvoyes(compte.email)).isEqualTo(2); // inscription + 1 seul renvoi
    }

    @Test
    void renvoiPourUnEmailInconnu_202_etRienNestPublie() throws Exception {
        String inconnu = "personne-" + UUID.randomUUID() + "@exemple.ma";

        assertThat(renvoyer(inconnu).statusCode()).isEqualTo(202);
        assertThat(nombreDeCodesEnvoyes(inconnu)).isZero();
    }

    // ---------- outils ----------

    record Compte(String phone, String email) {
        String json() {
            return "{\"phone\":\"" + phone + "\",\"password\":\"" + PASSWORD + "\",\"displayName\":\"Yassir\","
                    + "\"email\":\"" + email + "\",\"birthDate\":\"2001-05-14\",\"phoneCountry\":\"MA\"}";
        }
    }

    static Compte nouveauCompte() {
        String chiffres = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        return new Compte("+2126" + chiffres, "joueur" + chiffres + "@exemple.ma");
    }

    String codeEnvoye(String email) {
        return jdbc.sql("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(payload, '$.data.code')) FROM outbox_event
                WHERE event_type = 'identity.email_verification_requested'
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.data.email')) = :email
                ORDER BY position DESC LIMIT 1
                """).param("email", email).query(String.class).single();
    }

    long nombreDeCodesEnvoyes(String email) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'identity.email_verification_requested'
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.data.email')) = :email
                """).param("email", email).query(Long.class).single();
    }

    HttpResponse<String> inscrire(Compte compte) throws Exception {
        HttpResponse<String> reponse = post("/api/v1/auth/register", compte.json());
        assertThat(reponse.statusCode()).isEqualTo(201);
        return reponse;
    }

    HttpResponse<String> connexion(String phone, String password) throws Exception {
        return post("/api/v1/auth/login", "{\"phone\":\"" + phone + "\",\"password\":\"" + password + "\"}");
    }

    HttpResponse<String> verifier(String email, String code) throws Exception {
        return post("/api/v1/auth/email/verify", "{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}");
    }

    HttpResponse<String> renvoyer(String email) throws Exception {
        return post("/api/v1/auth/email/resend", "{\"email\":\"" + email + "\"}");
    }

    HttpResponse<String> post(String path, String json) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
    }

    URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    static Map<String, Object> json(HttpResponse<String> response) throws Exception {
        return JSONObjectUtils.parse(response.body());
    }
}
