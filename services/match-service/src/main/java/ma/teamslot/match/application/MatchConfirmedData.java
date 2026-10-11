package ma.teamslot.match.application;

import java.util.UUID;

/** Champ data de match.confirmed (docs/events.md). */
public record MatchConfirmedData(UUID matchId) {
}
