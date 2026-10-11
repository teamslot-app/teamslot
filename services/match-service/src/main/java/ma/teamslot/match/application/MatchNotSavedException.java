package ma.teamslot.match.application;

import java.util.UUID;

/**
 * Le créneau est réservé chez venue mais le match n'a pas pu être enregistré.
 * Compensation prévue dans SCRUM-76 : venue libère le créneau en consommant match.cancelled.
 */
public class MatchNotSavedException extends RuntimeException {
    public MatchNotSavedException(UUID matchId, UUID slotId, Throwable cause) {
        super("Créneau réservé mais match non enregistré (à libérer, SCRUM-76) : matchId="
                + matchId + " slotId=" + slotId, cause);
    }
}
