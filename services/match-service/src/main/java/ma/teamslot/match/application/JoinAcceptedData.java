package ma.teamslot.match.application;

import java.util.UUID;

/** Champ data de match.join_accepted (docs/events.md) : matchId, userId. */
public record JoinAcceptedData(UUID matchId, UUID userId) {
}
