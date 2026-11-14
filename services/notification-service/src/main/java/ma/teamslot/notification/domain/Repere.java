package ma.teamslot.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Position dans la liste « Mes notifications » : la dernière notification déjà lue par le front.
 * La tranche suivante commence juste après (date plus ancienne, ou même date et id plus petit).
 * L'id départage deux notifications créées au même instant (match.confirmed en crée plusieurs d'un coup).
 */
public record Repere(Instant creeLe, UUID id) {

    public Repere {
        Objects.requireNonNull(creeLe);
        Objects.requireNonNull(id);
    }

    public static Repere de(Notification notification) {
        return new Repere(notification.creeLe(), notification.id());
    }
}
