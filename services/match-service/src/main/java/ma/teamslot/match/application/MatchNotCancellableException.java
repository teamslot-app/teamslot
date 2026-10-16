package ma.teamslot.match.application;

/** Le match est déjà annulé, joué ou terminé : il ne peut plus être annulé (réponse 409). */
public class MatchNotCancellableException extends RuntimeException {
    public MatchNotCancellableException(String statut) {
        super("Le match ne peut plus être annulé : " + statut);
    }
}
