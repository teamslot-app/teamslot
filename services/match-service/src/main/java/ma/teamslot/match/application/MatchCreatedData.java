package ma.teamslot.match.application;

import java.time.Instant;
import java.util.UUID;

/** Champ data de match.created : exactement les champs du catalogue docs/events.md (v1.1). */
public record MatchCreatedData(UUID matchId, String kind, String visibility, UUID ownerId,
                               UUID payerId, UUID venueId, UUID slotId, Instant startsAt,
                               long totalAmount, String currency, boolean acceptsOnSitePayment) {
}
