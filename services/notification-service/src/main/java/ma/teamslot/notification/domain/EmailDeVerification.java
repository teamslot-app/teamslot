package ma.teamslot.notification.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private static final ZoneId MAROC = ZoneId.of("Africa/Casablanca");
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm");

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

    /** Heure d'expiration, à l'heure du Maroc, ex. « 14:20 ». */
    public String heureLimite() {
        return HEURE.format(expireA.atZone(MAROC));
    }

    public String texte() {
        return """
                Bonjour %s,

                Ton code de vérification TeamSlot est : %s

                Il est valable jusqu'à %s (heure du Maroc). Saisis-le dans l'application pour activer ton compte.

                Si tu n'as pas créé de compte TeamSlot, ignore ce message : personne ne pourra utiliser ton adresse sans ce code.
                """.formatted(nomAffiche, code, heureLimite());
    }

    /** Version HTML : le nom affiché vient de l'utilisateur, il est échappé (pas d'injection de HTML). */
    public String html() {
        return """
                <!DOCTYPE html>
                <html lang="fr"><body style="font-family: Arial, sans-serif; color: #1f2933">
                <p>Bonjour %s,</p>
                <p>Ton code de vérification TeamSlot est :</p>
                <p style="font-size: 32px; font-weight: bold; letter-spacing: 6px">%s</p>
                <p>Il est valable jusqu'à <strong>%s</strong> (heure du Maroc). Saisis-le dans l'application pour activer ton compte.</p>
                <p style="color: #616e7c">Si tu n'as pas créé de compte TeamSlot, ignore ce message : personne ne pourra utiliser ton adresse sans ce code.</p>
                </body></html>
                """.formatted(echapper(nomAffiche), code, heureLimite());
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
