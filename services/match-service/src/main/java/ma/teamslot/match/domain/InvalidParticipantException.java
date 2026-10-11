package ma.teamslot.match.domain;

/** Regle du participant non respectee : reponse 400. */
public class InvalidParticipantException extends RuntimeException {
    public InvalidParticipantException(String message) {
        super(message);
    }
}
