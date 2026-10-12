package ma.teamslot.match.application;

/** Participant deja present dans le match : reponse 409 participant-already-added. */
public class ParticipantAlreadyAddedException extends RuntimeException {

    /** Ce qui est deja present : le compte, le surnom, ou l'organisateur lui-meme. */
    public enum Raison { COMPTE, SURNOM, ORGANISATEUR }

    private final Raison raison;

    public ParticipantAlreadyAddedException(Raison raison, Throwable cause) {
        super("Participant deja present dans le match : " + raison, cause);
        this.raison = raison;
    }

    public Raison raison() {
        return raison;
    }
}
