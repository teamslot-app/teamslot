package ma.teamslot.venue.application;

import java.util.UUID;

/** Champ data de slot.released : exactement les champs du catalogue docs/events.md. */
public record SlotReleasedData(UUID venueId, UUID slotId, UUID matchId, String reason) {}
