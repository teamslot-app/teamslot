package ma.teamslot.notification.adapter.in.kafka;

import java.util.Objects;
import java.util.UUID;

/** Champs de match.join_accepted (docs/events.md, SCRUM-79) : le joueur accepté a toujours un compte. */
public record JoinAcceptedData(UUID matchId, UUID userId) {

    public JoinAcceptedData {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(userId, "userId");
    }
}
