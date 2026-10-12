package ma.teamslot.match.application;

/** Le match n'accepte plus de participants (annule, joue...) : reponse 409. */
public class MatchClosedException extends RuntimeException {
    public MatchClosedException(String statut) {
        super("Le match n'accepte plus de participants : " + statut);
    }
}
