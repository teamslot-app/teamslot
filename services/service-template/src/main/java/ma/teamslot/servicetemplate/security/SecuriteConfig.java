package ma.teamslot.servicetemplate.security;

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
 * Verification du JWT dans chaque service (ADR-013, defense en profondeur, SCRUM-87).
 * Jeton signe par identity-service en RS256, verifie avec la cle publique du JWKS.
 * Emetteur impose (teamslot-identity), expiration verifiee.
 * Identite de l'appelant : claim "sub" du jeton, jamais un en-tete.
 * Publics : sondes de sante (Kubernetes) et metriques (Prometheus, non routees par la passerelle).
 */
@Configuration
public class SecuriteConfig {

    private static final String[] PUBLICS = {"/actuator/health/**", "/actuator/prometheus"};

    @Bean
    SecurityFilterChain securite(HttpSecurity http) {
        http
                .authorizeHttpRequests(regles -> regles
                        .requestMatchers(PUBLICS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(serveur -> serveur.jwt(Customizer.withDefaults()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // API sans cookie de session : jeton dans l'en-tete Authorization, CSRF sans objet.
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    /** RS256 seulement (refuse aussi "alg: none") et emetteur impose. */
    @Bean
    JwtDecoder jwtDecoder(@Value("${teamslot.jwt.jwk-set-uri}") String jwkSetUri,
                          @Value("${teamslot.jwt.issuer}") String emetteur) {
        NimbusJwtDecoder decodeur = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decodeur.setJwtValidator(JwtValidators.createDefaultWithIssuer(emetteur));
        return decodeur;
    }
}
