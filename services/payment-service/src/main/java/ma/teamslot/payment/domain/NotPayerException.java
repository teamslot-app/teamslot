package ma.teamslot.payment.domain;

/** L'appelant n'est pas le responsable du paiement : reponse 403. */
public class NotPayerException extends RuntimeException {
    public NotPayerException() {
        super("Seul le responsable du paiement peut faire cette action.");
    }
}
