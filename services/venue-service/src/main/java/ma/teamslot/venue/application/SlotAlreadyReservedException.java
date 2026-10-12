package ma.teamslot.venue.application;

import java.util.UUID;

public class SlotAlreadyReservedException extends RuntimeException {

  public SlotAlreadyReservedException(UUID slotId) {
    super("Créneau déjà réservé : " + slotId);
  }
}
