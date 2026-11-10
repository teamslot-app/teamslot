package ma.teamslot.match.application;

import java.util.UUID;

/** Champ data de match.join_requested (docs/events.md) : matchId, userId. */
public record JoinRequestedData(UUID matchId, UUID userId) {
}
