package ma.teamslot.notification.domain;

import java.time.Instant;
import java.util.UUID;
import java.util.Objects;

/**
 * Une notification de la liste « Mes notifications » (SCRUM-81, contrat docs/api/notification.yaml).
 * Ni texte libre ni donnée personnelle : le front construit le message à partir de type et matchId.
 *
 * @param lueLe null tant que la notification n'est pas lue
 */
public record Notification(UUID id, TypeNotification type, UUID matchId, Instant creeLe, Instant lueLe) {

    public Notification {
        Objects.requireNonNull(id);
        Objects.requireNonNull(type);
        Objects.requireNonNull(matchId);
        Objects.requireNonNull(creeLe);
    }
}
