package ma.teamslot.match.domain;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Un participant d'un match (SCRUM-60) : soit un compte (userId), soit un invite sans compte
 * identifie par un surnom (guestName), jamais les deux.
 * Aucun numero ni e-mail n'est conserve pour un invite : le surnom les refuse.
 */
public record Participant(UUID id, UUID matchId, UUID userId, String guestName,
                          ParticipantStatus status) {

    public static final int SURNOM_MAX = 40;

    /** Lettres, chiffres, espaces, apostrophe, tiret et point ; pas de @ ni de caractere de controle. */
    private static final Pattern SURNOM_AUTORISE = Pattern.compile("[\\p{L}\\p{M}\\p{N} '.-]+");
    /** A partir de six chiffres, le surnom ressemble a un numero de telephone. */
    private static final int CHIFFRES_MAX = 5;

    public Participant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(status, "status");
        if ((userId == null) == (guestName == null)) {
            throw new InvalidParticipantException("Un participant est soit un compte, soit un surnom, jamais les deux.");
        }
    }

    /** Ajoute un compte : il devra confirmer sa participation. */
    public static Participant compte(UUID matchId, UUID userId) {
        Objects.requireNonNull(userId, "userId");
        return new Participant(UUID.randomUUID(), matchId, userId, null, ParticipantStatus.INVITED);
    }

    /** Ajoute un invite sans compte : confirme d'office, l'organisateur repond de lui. */
    public static Participant invite(UUID matchId, String surnom) {
        return new Participant(UUID.randomUUID(), matchId, null, surnomValide(surnom),
                ParticipantStatus.CONFIRMED);
    }

    static String surnomValide(String surnom) {
        if (surnom == null) {
            throw new InvalidParticipantException("Le surnom est obligatoire.");
        }
        String nettoye = surnom.strip().replaceAll("\\s+", " ");
        if (nettoye.isEmpty() || nettoye.length() > SURNOM_MAX) {
            throw new InvalidParticipantException("Le surnom doit faire de 1 a " + SURNOM_MAX + " caracteres.");
        }
        if (!SURNOM_AUTORISE.matcher(nettoye).matches()
                || nettoye.chars().filter(Character::isDigit).count() > CHIFFRES_MAX) {
            throw new InvalidParticipantException(
                    "Le surnom ne peut contenir que des lettres, des chiffres, des espaces, ' . et -, sans e-mail ni numero.");
        }
        return nettoye;
    }
}
