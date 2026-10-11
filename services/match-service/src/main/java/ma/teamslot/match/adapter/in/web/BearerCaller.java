package ma.teamslot.match.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import ma.teamslot.match.application.CallerNotAuthenticatedException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lit l'appelant dans le jeton : claim "sub" (ADR-013). La passerelle ne pose aucun en-tête
 * X-User-Id et supprime celui que le client enverrait.
 *
 * ATTENTION : la signature n'est PAS vérifiée ici. Elle l'est par venue-service sur le jeton
 * transmis, et par une vérification commune prévue dans SCRUM-87. À remplacer dès qu'elle existe.
 */
final class BearerCaller {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private BearerCaller() {
    }

    static String token(HttpServletRequest http) {
        String header = http.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new CallerNotAuthenticatedException();
        }
        String token = header.substring(7).trim();
        if (token.isEmpty()) {
            throw new CallerNotAuthenticatedException();
        }
        return token;
    }

    static UUID subject(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new CallerNotAuthenticatedException();
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            Map<?, ?> claims = JSON.readValue(payload, Map.class);
            return UUID.fromString(String.valueOf(claims.get("sub")));
        } catch (RuntimeException e) {
            throw new CallerNotAuthenticatedException();
        }
    }
}
