package ma.teamslot.venue.application;

import java.util.UUID;
import ma.teamslot.venue.domain.Money;

/** Schéma Price du contrat venue.yaml. */
public record PriceView(UUID slotId, Money base, Money equipmentTotal, Money total) {}
