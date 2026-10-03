package ma.teamslot.apigateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * SCRUM-67 — tests de sécurité S3 (jetons invalides) et règles d'accès de la passerelle.
 * Les tests fabriquent leurs propres clés RSA : identity-service n'a pas besoin de tourner.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.allow-bean-definition-overriding=true")
class JwtSecurityTests {

    static final String ISSUER = "teamslot-identity";
    /** La « vraie » clé d'identity pendant les tests. */
    static final RSAKey IDENTITY_KEY = newKey("identity");
    /** Une autre clé, celle d'un attaquant qui essaie de signer ses propres jetons. */
    static final RSAKey ATTACKER_KEY = newKey("attacker");

    /** Remplace le décodeur réel (qui télécharge la clé d'identity) par la clé de test. */
    @TestConfiguration
    static class TestKeys {
        @Bean
        ReactiveJwtDecoder jwtDecoder() throws Exception {
            NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                    .withPublicKey(IDENTITY_KEY.toRSAPublicKey())
                    .signatureAlgorithm(SignatureAlgorithm.RS256)
                    .build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
            return decoder;
        }
    }

    @Value("${local.server.port}")
    int port;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    // ---------- accès sans jeton ----------

    @Test
    void sansJeton_routeProtegee_401() {
        client.get().uri("/api/v1/matches").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void sansJeton_connexion_estAutorisee() {
        // la passerelle laisse passer ; le service identity n'est pas lancé,
        // donc la réponse n'est pas 200, mais surtout ce n'est ni 401 ni 403
        client.post().uri("/api/v1/auth/login").exchange()
                .expectStatus().value(status -> assertThat(status).isNotIn(401, 403));
    }

    @Test
    void sansJeton_santeDeLaPasserelle_200() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    // ---------- jeton valide ----------

    @Test
    void jetonValide_routeProtegee_passeLaPasserelle() {
        client.get().uri("/api/v1/matches")
                .headers(h -> h.setBearerAuth(token(IDENTITY_KEY, ISSUER, inMinutes(15))))
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotIn(401, 403));
    }

    @Test
    void jetonValide_endpointInterne_refuse() {
        client.post().uri("/api/v1/slot-reservations")
                .headers(h -> h.setBearerAuth(token(IDENTITY_KEY, ISSUER, inMinutes(15))))
                .exchange()
                .expectStatus().isForbidden();
    }

    // ---------- S3 : jetons invalides ----------

    @Test
    void S3_jetonExpire_401() {
        client.get().uri("/api/v1/matches")
                .headers(h -> h.setBearerAuth(token(IDENTITY_KEY, ISSUER, inMinutes(-5))))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void S3_signatureModifiee_401() {
        // jeton bien formé mais signé avec une autre clé que celle d'identity
        client.get().uri("/api/v1/matches")
                .headers(h -> h.setBearerAuth(token(ATTACKER_KEY, ISSUER, inMinutes(15))))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void S3_algorithmeNone_401() {
        // jeton sans signature (« alg: none »)
        String unsigned = new PlainJWT(claims(ISSUER, inMinutes(15))).serialize();
        client.get().uri("/api/v1/matches")
                .headers(h -> h.setBearerAuth(unsigned))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void S3_mauvaisEmetteur_401() {
        client.get().uri("/api/v1/matches")
                .headers(h -> h.setBearerAuth(token(IDENTITY_KEY, "quelqu-un-d-autre", inMinutes(15))))
                .exchange().expectStatus().isUnauthorized();
    }

    // ---------- outils ----------

    static RSAKey newKey(String id) {
        try {
            return new RSAKeyGenerator(2048).keyID(id).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static Instant inMinutes(long minutes) {
        return Instant.now().plus(minutes, ChronoUnit.MINUTES);
    }

    static JWTClaimsSet claims(String issuer, Instant expiresAt) {
        return new JWTClaimsSet.Builder()
                .subject("11111111-1111-1111-1111-111111111111")
                .issuer(issuer)
                .issueTime(new Date())
                .expirationTime(Date.from(expiresAt))
                .build();
    }

    static String token(RSAKey key, String issuer, Instant expiresAt) {
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                    claims(issuer, expiresAt));
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
