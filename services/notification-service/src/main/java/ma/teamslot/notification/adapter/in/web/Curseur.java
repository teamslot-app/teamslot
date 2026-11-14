package ma.teamslot.notification.adapter.in.web;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;
import ma.teamslot.notification.domain.Repere;

/**
 * Curseur opaque du contrat (schéma Cursor, 200 caractères au plus) : "v1|date|id" encodé en Base64URL.
 * Le front le renvoie tel quel sans l'interpréter. Il n'est pas secret : un curseur modifié à la main
 * déplace seulement la lecture dans SES propres notifications (le filtre user_id vient toujours du jeton).
 */
final class Curseur {

    static final int LONGUEUR_MAX = 200;
    private static final String VERSION = "v1";

    private Curseur() {
    }

    static String ecrire(Repere repere) {
        String clair = VERSION + "|" + repere.creeLe() + "|" + repere.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(clair.getBytes(StandardCharsets.UTF_8));
    }

    /** null si absent (première tranche) ; CurseurInvalideException s'il est illisible (réponse 400). */
    static Repere lire(String curseur) {
        if (curseur == null) {
            return null;
        }
        if (curseur.isEmpty() || curseur.length() > LONGUEUR_MAX) {
            throw new CurseurInvalideException();
        }
        try {
            String[] parties = new String(Base64.getUrlDecoder().decode(curseur), StandardCharsets.UTF_8).split("\\|");
            if (parties.length != 3 || !VERSION.equals(parties[0])) {
                throw new CurseurInvalideException();
            }
            return new Repere(Instant.parse(parties[1]), UUID.fromString(parties[2]));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new CurseurInvalideException();
        }
    }

    static final class CurseurInvalideException extends RuntimeException {
        CurseurInvalideException() {
            super("Curseur illisible");
        }
    }
}
