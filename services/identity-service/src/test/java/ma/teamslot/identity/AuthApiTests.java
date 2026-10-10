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

/**
 * SCRUM-57 — critères d'acceptation : inscription, connexion, renouvellement, JWKS.
 * Appels HTTP réels sur un port aléatoire, base H2 du profil de test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthApiTests {

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
