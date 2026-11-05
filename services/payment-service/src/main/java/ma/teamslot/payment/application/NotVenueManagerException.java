package ma.teamslot.payment.application;

/**
 * SCRUM-75, test S14 : l'appelant n'est pas le gérant du terrain du match (joueur, gérant d'un autre terrain,
 * jeton sans rôle, ou terrain inconnu de payment) : 403. Refus par défaut.
 */
public class NotVenueManagerException extends RuntimeException {
    public NotVenueManagerException() {
        super("Seul le gérant du terrain du match peut confirmer l'encaissement.");
    }
}
