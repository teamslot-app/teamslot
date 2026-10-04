package ma.teamslot.apigateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * SCRUM-67 — sécurité de la passerelle.
 *
 * La passerelle est la seule porte d'entrée de l'API (frontière TB1 du modèle de menaces).
 * Elle vérifie chaque JWT (signature RS256, expiration, émetteur), puis transmet la requête
 * avec son jeton : chaque service le revérifie (défense en profondeur, conception 11.5).
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                // API sans état, appelée avec un jeton : pas de session, donc pas de CSRF
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // sonde de santé pour Kubernetes
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // inscription, connexion, renouvellement : pas encore de jeton
                        .pathMatchers("/api/v1/auth/**").permitAll()
                        // service modèle de la démo du Sprint 0 (walking skeleton)
                        .pathMatchers("/api/v1/template/**").permitAll()
                        // endpoint interne de venue : jamais accessible depuis l'extérieur
                        .pathMatchers("/api/v1/slot-reservations", "/api/v1/slot-reservations/**").denyAll()
                        // tout le reste exige un jeton valide
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    /**
     * Décodeur de jetons : récupère la clé publique d'identity (JWKS), n'accepte que RS256
     * (donc refuse « alg: none » et tout autre algorithme), et vérifie l'expiration et l'émetteur.
     * La clé n'est téléchargée qu'à la première requête : la passerelle démarre même si
     * identity n'est pas encore lancé.
     */
    @Bean
    ReactiveJwtDecoder jwtDecoder(@Value("${teamslot.jwt.jwk-set-uri}") String jwkSetUri,
                                  @Value("${teamslot.jwt.issuer}") String issuer) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }
}
