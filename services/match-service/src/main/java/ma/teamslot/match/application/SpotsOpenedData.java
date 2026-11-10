package ma.teamslot.match.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Champ data de match.spots_opened (docs/events.md) : matchId, venueId, startsAt, openSpots.
 * Aucun participant : l'événement sert à trouver le match (search-service, plus tard), pas à savoir qui joue.
 */
public record SpotsOpenedData(UUID matchId, UUID venueId, Instant startsAt, int openSpots) {
}
