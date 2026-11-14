package ma.teamslot.notification.adapter.in.kafka;

import java.util.Objects;
import java.util.UUID;

/**
 * Champs de match.participant_added (docs/events.md) : un compte (userId) OU un invité (guestName), jamais les deux.
 * Message reçu = entrée non fiable : un champ manquant ou les deux à la fois rend le message invalide (.dlt).
 * Le surnom de l'invité n'est pas gardé : seul compte le fait qu'il n'y a pas de compte à prévenir.
 */
public record ParticipantAddedData(UUID matchId, UUID participantId, UUID userId, String guestName) {

    public ParticipantAddedData {
        Objects.requireNonNull(matchId, "matchId");
        boolean compte = userId != null;
        boolean invite = guestName != null && !guestName.isBlank();
        if (compte == invite) {
            throw new IllegalArgumentException("match.participant_added : exactement un de userId ou guestName");
        }
    }
}
