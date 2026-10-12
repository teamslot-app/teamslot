package ma.teamslot.match.application;

/** L'appelant n'est pas l'organisateur du match : reponse 403. */
public class NotMatchOrganizerException extends RuntimeException {
    public NotMatchOrganizerException() {
        super("Seul l'organisateur du match peut faire cette action.");
    }
}
