package ma.teamslot.venue.application;

import java.time.Instant;
import java.util.UUID;
import ma.teamslot.venue.domain.Money;

/** Schéma Slot du contrat venue.yaml. */
public record SlotView(UUID id, Instant start, Instant end, boolean available, Money price) {}
