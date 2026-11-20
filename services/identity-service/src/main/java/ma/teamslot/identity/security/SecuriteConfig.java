package ma.teamslot.identity.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import ma.teamslot.identity.application.TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SCRUM-95 : identity devient aussi serveur de ressources (ADR-013, défense en profondeur, comme SCRUM-87).
 * /api/v1/users/** exige un jeton RS256 valide, émis par identity (claim iss), non expiré ; l'appelant est la
 * claim sub. Les autres routes (inscription, connexion, JWKS, sondes, version) gardent leur comportement d'avant :
 * elles restent publiques.
 * La clé de vérification est la clé publique qu'identity publie déjà dans son JWKS (TokenService) : aucun appel
 * réseau vers lui-même, et la même rotation de clé s'applique aux deux.
 */
@Configuration
public class SecuriteConfig {

    @Bean
    SecurityFilterChain securite(HttpSecurity http) {
        http
                .authorizeHttpRequests(regles -> regles
                        .requestMatchers("/api/v1/users/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(serveur -> serveur.jwt(Customizer.withDefaults()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // API sans cookie de session : jeton dans l'en-tête Authorization, CSRF sans objet.
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    /** RS256 seulement (refuse aussi « alg: none »), émetteur imposé, expiration vérifiée. */
    @Bean
    JwtDecoder jwtDecoder(TokenService jetons, @Value("${teamslot.jwt.issuer}") String emetteur) throws Exception {
        RSAKey cle = (RSAKey) JWKSet.parse(jetons.publicJwks()).getKeys().get(0);
        NimbusJwtDecoder decodeur = NimbusJwtDecoder.withPublicKey(cle.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decodeur.setJwtValidator(JwtValidators.createDefaultWithIssuer(emetteur));
        return decodeur;
    }
}
