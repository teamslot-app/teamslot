package ma.teamslot.match.application;

/** Ce compte participe deja au match : reponse 409. */
public class ParticipantAlreadyAddedException extends RuntimeException {
    public ParticipantAlreadyAddedException(Throwable cause) {
        super("Ce compte participe deja au match.", cause);
    }
}
