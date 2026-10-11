package ma.teamslot.match.adapter.in.web;

import java.util.UUID;
import ma.teamslot.match.application.CallerNotAuthenticatedException;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Identite de l'appelant : claim "sub" du jeton DEJA VERIFIE par SecuriteConfig
 * (signature RS256, emetteur, expiration ; ADR-013, SCRUM-87). Remplace BearerCaller, qui lisait
 * le jeton sans verifier sa signature.
 */
final class Appelant {

    private Appelant() {
    }

    static UUID de(Jwt jeton) {
        try {
            return UUID.fromString(jeton.getSubject());
        } catch (RuntimeException e) {
            throw new CallerNotAuthenticatedException();
        }
    }
}
