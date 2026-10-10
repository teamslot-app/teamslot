package ma.teamslot.match.application;

import java.util.UUID;

/** Champ data de match.cancelled (catalogue : matchId, reason, cancelledBy). */
public record MatchCancelledData(UUID matchId, String reason, UUID cancelledBy) {
}
