package ma.teamslot.venue.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ma.teamslot.venue.domain.Money;

/** Schéma SlotReservation du contrat venue.yaml. */
public record ReservationView(
    UUID slotId,
    UUID matchId,
    UUID venueId,
    List<EquipmentView> equipment,
    Money totalPrice,
    boolean acceptsOnSitePayment,
    String status,
    Instant startsAt,
    Instant endsAt) {

  /** Schéma Equipment du contrat. */
  public record EquipmentView(UUID id, String name, Money extraPrice) {}
}
