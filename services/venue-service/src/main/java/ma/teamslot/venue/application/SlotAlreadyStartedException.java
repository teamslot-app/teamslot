package ma.teamslot.venue.application;

import java.util.UUID;

public class SlotAlreadyStartedException extends RuntimeException {

  public SlotAlreadyStartedException(UUID slotId) {
    super("Créneau déjà commencé : " + slotId);
  }
}
