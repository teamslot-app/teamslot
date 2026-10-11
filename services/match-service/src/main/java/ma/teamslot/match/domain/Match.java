package ma.teamslot.match.domain;

import java.time.Instant;
import java.util.UUID;

/** Un match de groupe. Les montants sont en centimes (docs/events.md). */
public record Match(
        UUID id, String kind, String status, String visibility, int openSpots,
        UUID ownerId, UUID payerId, UUID venueId, UUID slotId, Instant startsAt,
        long totalAmountCents, String currency, boolean acceptsOnSitePayment, long version) {
}
