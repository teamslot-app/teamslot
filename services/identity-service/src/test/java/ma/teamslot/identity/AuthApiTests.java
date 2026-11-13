package ma.teamslot.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.SignedJWT;
import ma.teamslot.identity.adapter.out.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * SCRUM-57 — critères d'acceptation : inscription, connexion, renouvellement, JWKS.
 * Appels HTTP réels sur un port aléatoire, MySQL et Redis (Testcontainers).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthApiTests {

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        RedisTestContainer.register(registry);
    }

    static final String PASSWORD = "MotDePasse-Solide-42";

    @Value("${local.server.port}")
    int port;

    @Autowired
    UserRepository users;

    final HttpClient http = HttpClient.newHttpClient();

    // ---------- inscription ----------

    @Test
    void inscriptionValide_201_sansMotDePasseDansLaReponse() throws Exception {
        String phone = newPhone();
        HttpResponse<String> response = register(phone, PASSWORD);

        assertThat(response.statusCode()).isEqualTo(201);
        Map<String, Object> body = json(response);
        assertThat(body.get("phone")).isEqualTo(phone);
        assertThat(body.get("platformRole")).isEqualTo("PLAYER");
        assertThat(response.body()).doesNotContain(PASSWORD).doesNotContainIgnoringCase("password");
    }

    @Test
    void motDePasseStockeHacheEtJamaisEnClair() throws Exception {
        String phone = newPhone();
        register(phone, PASSWORD);

        String hash = users.findByPhone(phone).orElseThrow().getPasswordHash();
        assertThat(hash).isNotEqualTo(PASSWORD).startsWith("$2");
    }

    @Test
    void telephoneDejaUtilise_409_problemDetails() throws Exception {
        String phone = newPhone();
        register(phone, PASSWORD);
        HttpResponse<String> second = register(phone, PASSWORD);

        assertThat(second.statusCode()).isEqualTo(409);
        assertThat(second.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
    }

    @Test
    void motDePasseTropCourt_400() throws Exception {
        assertThat(register(newPhone(), "court").statusCode()).isEqualTo(400);
    }

    // ---------- inscription v2 (SCRUM-132) ----------

    @Test
    void inscriptionV2_201_emailEnMinusculesEtNonVerifie() throws Exception {
        String phone = newPhone();
        String email = "Joueur" + phone.substring(5) + "@Exemple.MA";
        HttpResponse<String> response = registerV2(phone, PASSWORD, email, "2001-05-14", "MA");

        assertThat(response.statusCode()).isEqualTo(201);
        Map<String, Object> user = json(response);
        assertThat(user.get("email")).isEqualTo(email.toLowerCase());
        assertThat(user.get("emailVerified")).isEqualTo(false);
        assertThat(user.get("birthDate")).isEqualTo("2001-05-14");
        assertThat(user.get("phoneCountry")).isEqualTo("MA");
    }

    @Test
    void motDePasseFaible_400_avecLaListeDesRegles() throws Exception {
        HttpResponse<String> response = register(newPhone(), "toutenminuscules");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json(response).get("type")).isEqualTo("https://teamslot.app/problems/weak-password");
        assertThat((java.util.List<?>) json(response).get("violations")).isNotEmpty();
    }

    @Test
    void motDePasseContenantLeNumero_400() throws Exception {
        String phone = newPhone();
        assertThat(register(phone, "Match-" + phone.substring(phone.length() - 6) + "-Soir").statusCode())
                .isEqualTo(400);
    }

    @Test
    void emailDejaUtilise_409_messageGenerique() throws Exception {
        String email = "doublon" + newPhone().substring(5) + "@exemple.ma";
        registerV2(newPhone(), PASSWORD, email, null, null);
        HttpResponse<String> second = registerV2(newPhone(), PASSWORD, email.toUpperCase(), null, null);

        assertThat(second.statusCode()).isEqualTo(409);
        assertThat(json(second).get("type")).isEqualTo("https://teamslot.app/problems/account-exists");
        // le message ne dit pas si c'est le numéro ou l'e-mail qui existe déjà
        assertThat((String) json(second).get("detail")).contains("numéro ou cette adresse e-mail");
    }

    @Test
    void numeroQuiNeCorrespondPasAuPays_400() throws Exception {
        HttpResponse<String> response = registerV2(newPhone(), PASSWORD, null, null, "FR");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json(response).get("type")).isEqualTo("https://teamslot.app/problems/invalid-phone");
    }

    @Test
    void dateDeNaissanceDansLeFutur_ouAvant1900_400() throws Exception {
        String demain = java.time.LocalDate.now().plusDays(2).toString();
        assertThat(registerV2(newPhone(), PASSWORD, null, demain, null).statusCode()).isEqualTo(400);
        assertThat(registerV2(newPhone(), PASSWORD, null, "1899-12-31", null).statusCode()).isEqualTo(400);
    }

    @Test
    void emailMalForme_400() throws Exception {
        assertThat(registerV2(newPhone(), PASSWORD, "pas-un-email", null, null).statusCode()).isEqualTo(400);
    }

    // ---------- type de compte (SCRUM-136) ----------

    @Test
    void sansTypeDeCompte_joueurSansDemande() throws Exception {
        Map<String, Object> user = json(register(newPhone(), PASSWORD));

        assertThat(user.get("platformRole")).isEqualTo("PLAYER");
        assertThat(user.get("managerStatus")).isEqualTo("NONE");
    }

    @Test
    void roleOuTypeDeCompteEnvoyes_ignores_resteJoueurSansDemande() throws Exception {
        // affectation de masse : un client ne peut pas se donner un rôle en ajoutant des champs à l'inscription
        String phone = newPhone();
        HttpResponse<String> response = post("/api/v1/auth/register", "{\"phone\":\"" + phone + "\",\"password\":\""
                + PASSWORD + "\",\"displayName\":\"Yassir\",\"accountType\":\"VENUE_MANAGER\","
                + "\"platformRole\":\"ADMIN\",\"managerStatus\":\"APPROVED\"}");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(json(response).get("platformRole")).isEqualTo("PLAYER");
        assertThat(json(response).get("managerStatus")).isEqualTo("NONE");
        SignedJWT jwt = SignedJWT.parse((String) json(login(phone, PASSWORD)).get("accessToken"));
        assertThat(jwt.getJWTClaimsSet().getStringClaim("role")).isEqualTo("PLAYER");
    }

    // ---------- connexion ----------

    @Test
    void connexionValide_jetonRS256_verifiableAvecLeJwks() throws Exception {
        String phone = newPhone();
        String userId = (String) json(register(phone, PASSWORD)).get("id");

        HttpResponse<String> login = login(phone, PASSWORD);
        assertThat(login.statusCode()).isEqualTo(200);
        Map<String, Object> tokens = json(login);
        assertThat(((Number) tokens.get("expiresIn")).longValue()).isEqualTo(900);
        assertThat((String) tokens.get("refreshToken")).isNotBlank();

        // la passerelle fait exactement ceci : télécharger le JWKS et vérifier la signature
        RSAKey publicKey = (RSAKey) JWKSet.parse(get("/.well-known/jwks.json").body()).getKeys().get(0);
        SignedJWT jwt = SignedJWT.parse((String) tokens.get("accessToken"));
        assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(jwt.verify(new RSASSAVerifier(publicKey))).isTrue();
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(userId);
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("teamslot-identity");
    }

    @Test
    void mauvaisMotDePasse_et_telephoneInconnu_memeReponse401() throws Exception {
        String phone = newPhone();
        register(phone, PASSWORD);

        HttpResponse<String> wrongPassword = login(phone, "Mauvais-Mot-De-Passe");
        HttpResponse<String> unknownPhone = login(newPhone(), PASSWORD);

        assertThat(wrongPassword.statusCode()).isEqualTo(401);
        assertThat(unknownPhone.statusCode()).isEqualTo(401);
        // aucune différence qui permettrait de savoir si le compte existe
        assertThat(json(wrongPassword).get("detail")).isEqualTo(json(unknownPhone).get("detail"));
        assertThat(json(wrongPassword).get("title")).isEqualTo(json(unknownPhone).get("title"));
    }

    // ---------- renouvellement ----------

    @Test
    void renouvellement_rotation_et_reutilisationDetectee() throws Exception {
        String phone = newPhone();
        register(phone, PASSWORD);
        String first = (String) json(login(phone, PASSWORD)).get("refreshToken");

        HttpResponse<String> refreshed = refresh(first);
        assertThat(refreshed.statusCode()).isEqualTo(200);
        String second = (String) json(refreshed).get("refreshToken");
        assertThat(second).isNotEqualTo(first);

        // réutiliser l'ancien jeton : refusé, et par sécurité le nouveau est révoqué aussi
        assertThat(refresh(first).statusCode()).isEqualTo(401);
        assertThat(refresh(second).statusCode()).isEqualTo(401);
    }

    // ---------- JWKS ----------

    @Test
    void jwks_neContientQueLaClePublique() throws Exception {
        HttpResponse<String> response = get("/.well-known/jwks.json");

        assertThat(response.statusCode()).isEqualTo(200);
        RSAKey key = (RSAKey) JWKSet.parse(response.body()).getKeys().get(0);
        assertThat(key.isPrivate()).isFalse();
        assertThat(key.getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(key.getKeyID()).isNotBlank();
    }

    // ---------- outils ----------

    static String newPhone() {
        return "+2126" + ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999);
    }

    HttpResponse<String> register(String phone, String password) throws Exception {
        return post("/api/v1/auth/register",
                "{\"phone\":\"" + phone + "\",\"password\":\"" + password + "\",\"displayName\":\"Yassir\"}");
    }

    /** Inscription v2 : les champs null ne sont pas envoyés (comme un ancien client). */
    HttpResponse<String> registerV2(String phone, String password, String email, String birthDate, String country)
            throws Exception {
        StringBuilder body = new StringBuilder("{\"phone\":\"" + phone + "\",\"password\":\"" + password
                + "\",\"displayName\":\"Yassir\"");
        if (email != null) {
            body.append(",\"email\":\"").append(email).append('"');
        }
        if (birthDate != null) {
            body.append(",\"birthDate\":\"").append(birthDate).append('"');
        }
        if (country != null) {
            body.append(",\"phoneCountry\":\"").append(country).append('"');
        }
        return post("/api/v1/auth/register", body.append('}').toString());
    }

    HttpResponse<String> login(String phone, String password) throws Exception {
        return post("/api/v1/auth/login", "{\"phone\":\"" + phone + "\",\"password\":\"" + password + "\"}");
    }

    HttpResponse<String> refresh(String refreshToken) throws Exception {
        return post("/api/v1/auth/refresh", "{\"refreshToken\":\"" + refreshToken + "\"}");
    }

    HttpResponse<String> post(String path, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    static Map<String, Object> json(HttpResponse<String> response) throws Exception {
        return JSONObjectUtils.parse(response.body());
    }
}
