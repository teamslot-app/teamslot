package ma.teamslot.match.application;

import java.util.UUID;

public class SlotNotFoundException extends RuntimeException {
    public SlotNotFoundException(UUID slotId) {
        super("Créneau introuvable : " + slotId);
    }
}
