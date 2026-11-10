package ma.teamslot.match.application;

/**
 * Le match a changé entre la lecture et l'écriture conditionnelle (participant ajouté, annulation...) :
 * réponse 409, le client recharge le match et réessaie.
 */
public class MatchChangedException extends RuntimeException {
    public MatchChangedException() {
        super("Le match a changé pendant la demande.");
    }
}
