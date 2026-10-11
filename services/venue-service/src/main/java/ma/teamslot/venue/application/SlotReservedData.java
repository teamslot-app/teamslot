package ma.teamslot.venue.application;

import java.time.Instant;
import java.util.UUID;

/** Champ data de slot.reserved : exactement les champs du catalogue docs/events.md. */
public record SlotReservedData(
    UUID venueId,
    UUID slotId,
    UUID matchId,
    Instant start,
    Instant end,
    long totalAmount,
    String currency) {}
