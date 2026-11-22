package ma.teamslot.notification.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * SCRUM-134 : e-mail qui porte le code de vérification d'un compte (événement identity.email_verification_requested).
 * Le code et l'adresse sont des données sensibles : toString() ne les montre jamais (un log ou une exception
 * qui afficherait cet objet ne les divulgue pas).
 */
public record EmailDeVerification(UUID userId, String destinataire, String nomAffiche, String code, Instant expireA) {

    public static final String SUJET = "Ton code TeamSlot";
    private static final Pattern FORMAT_CODE = Pattern.compile("\\d{6}");

    public EmailDeVerification {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(expireA, "expiresAt");
        if (destinataire == null || destinataire.isBlank()) {
            throw new IllegalArgumentException("e-mail du destinataire absent");
        }
        if (code == null || !FORMAT_CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("code de vérification invalide (6 chiffres attendus)");
        }
        nomAffiche = nomAffiche == null || nomAffiche.isBlank() ? "joueur" : nomAffiche.strip();
    }

    public boolean expireAvant(Instant instant) {
        return !expireA.isAfter(instant);
    }

    /**
     * Durée de validité restante, ex. « 15 minutes ». On n'écrit volontairement PAS une heure (« jusqu'à 14:20 ») :
     * le fuseau Africa/Casablanca a changé le 20/09/2026 (UTC+0, tzdata 2026c) et une JVM aux données de fuseau
     * plus anciennes afficherait une heure fausse d'une heure (constaté en local le 15/11). Une durée ne dépend
     * d'aucun fuseau.
     */
    public String dureeRestante(Instant maintenant) {
        long secondes = Math.max(0, Duration.between(maintenant, expireA).toSeconds());
        long minutes = Math.max(1, (secondes + 59) / 60);
        return minutes == 1 ? "1 minute" : minutes + " minutes";
    }

    public String texte(Instant maintenant) {
        return """
                Bonjour %s,

                Ton code de vérification TeamSlot est : %s

                Il est valable %s. Saisis-le dans l'application pour activer ton compte.

                Si tu n'as pas créé de compte TeamSlot, ignore ce message : personne ne pourra utiliser ton adresse sans ce code.
                """.formatted(nomAffiche, code, dureeRestante(maintenant));
    }

    /** Version HTML : le nom affiché vient de l'utilisateur, il est échappé (pas d'injection de HTML). */
    public String html(Instant maintenant) {
        return """
                <!DOCTYPE html>
                <html lang="fr"><body style="font-family: Arial, sans-serif; color: #1f2933">
                <p>Bonjour %s,</p>
                <p>Ton code de vérification TeamSlot est :</p>
                <p style="font-size: 32px; font-weight: bold; letter-spacing: 6px">%s</p>
                <p>Il est valable <strong>%s</strong>. Saisis-le dans l'application pour activer ton compte.</p>
                <p style="color: #616e7c">Si tu n'as pas créé de compte TeamSlot, ignore ce message : personne ne pourra utiliser ton adresse sans ce code.</p>
                </body></html>
                """.formatted(echapper(nomAffiche), code, dureeRestante(maintenant));
    }

    static String echapper(String texte) {
        StringBuilder sortie = new StringBuilder(texte.length());
        for (char c : texte.toCharArray()) {
            switch (c) {
                case '<' -> sortie.append("&lt;");
                case '>' -> sortie.append("&gt;");
                case '&' -> sortie.append("&amp;");
                case '"' -> sortie.append("&quot;");
                case '\'' -> sortie.append("&#39;");
                default -> sortie.append(c);
            }
        }
        return sortie.toString();
    }

    @Override
    public String toString() {
        return "EmailDeVerification[userId=" + userId + ", expireA=" + expireA + "]";
    }
}
