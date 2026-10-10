package ma.teamslot.identity.application;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * SCRUM-57 — émission des jetons d'accès (ADR-013).
 *
 * Signature RS256 avec une clé privée qui ne quitte jamais identity-service.
 * La clé publique est publiée en JWKS pour la passerelle et les services.
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private final RSAKey signingKey;
    private final RSASSASigner signer;
    private final String issuer;
    private final long accessTokenTtlSeconds;

    public TokenService(@Value("${teamslot.jwt.private-key:}") String privateKeyPem,
                        @Value("${teamslot.jwt.issuer}") String issuer,
                        @Value("${teamslot.jwt.access-token-ttl-seconds:900}") long accessTokenTtlSeconds) throws Exception {
        this.signingKey = privateKeyPem == null || privateKeyPem.isBlank() ? temporaryKey() : keyFromPem(privateKeyPem);
        this.signer = new RSASSASigner(signingKey);
        this.issuer = issuer;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    /** Jeton d'accès : sub = identifiant de l'utilisateur, iss, iat, exp. */
    public String createAccessToken(UUID userId) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .issuer(issuer)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(accessTokenTtlSeconds)))
                .build();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(signingKey.getKeyID())
                .type(JOSEObjectType.JWT)
                .build();
        try {
            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de signer le jeton", e);
        }
    }

    public long accessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    /** JWKS publié : clé publique seulement (toPublicJWK retire toutes les parties privées). */
    public Map<String, Object> publicJwks() {
        return new JWKSet(signingKey.toPublicJWK()).toJSONObject();
    }

    private static RSAKey temporaryKey() throws Exception {
        log.warn("Aucune clé JWT_PRIVATE_KEY fournie : clé RSA temporaire générée. "
                + "Les jetons ne survivront pas à un redémarrage (normal en local, pas en staging).");
        return new RSAKeyGenerator(2048)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyIDFromThumbprint(true)
                .generate();
    }

    /** Lit une clé privée RSA au format PEM PKCS#8 et en déduit la clé publique. */
    static RSAKey keyFromPem(String pem) throws Exception {
        String base64 = pem.replaceAll("-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\\s", "");
        KeyFactory factory = KeyFactory.getInstance("RSA");
        RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) factory.generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
                new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        RSAKey key = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        return new RSAKey.Builder(key).keyID(key.computeThumbprint().toString()).build();
    }
}
