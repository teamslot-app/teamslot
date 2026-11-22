package ma.teamslot.venue.application;

import java.util.UUID;
import ma.teamslot.venue.domain.Money;

public record EquipmentView(UUID id, String name, Money extraPrice) {}
