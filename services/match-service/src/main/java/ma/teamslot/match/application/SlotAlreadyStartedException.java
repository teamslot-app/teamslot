package ma.teamslot.match.application;

import java.util.UUID;

/** SCRUM-97 : venue refuse la réservation car le créneau a déjà commencé (409 slot-already-started). */
public class SlotAlreadyStartedException extends RuntimeException {
    public SlotAlreadyStartedException(UUID slotId) {
        super("Créneau déjà commencé : " + slotId);
    }
}
