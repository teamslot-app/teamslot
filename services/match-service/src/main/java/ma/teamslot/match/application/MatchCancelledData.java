package ma.teamslot.match.application;

import java.util.UUID;

/**
 * Champ data de match.cancelled (docs/events.md) : matchId, reason, cancelledBy.
 * reason est un code fixe, jamais un texte libre : aucune donnée personnelle dans l'événement.
 */
public record MatchCancelledData(UUID matchId, String reason, UUID cancelledBy) {
}
