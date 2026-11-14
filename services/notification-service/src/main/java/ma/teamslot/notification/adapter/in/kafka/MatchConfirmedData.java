package ma.teamslot.notification.adapter.in.kafka;

import java.util.Objects;
import java.util.UUID;

/** Champs de match.confirmed (docs/events.md) : seulement matchId. */
public record MatchConfirmedData(UUID matchId) {

    public MatchConfirmedData {
        Objects.requireNonNull(matchId, "matchId");
    }
}
