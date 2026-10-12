package ma.teamslot.payment.application;

/** Identite de l'appelant absente ou illisible dans le jeton : reponse 401. */
public class CallerNotAuthenticatedException extends RuntimeException {
    public CallerNotAuthenticatedException() {
        super("Authentification requise.");
    }
}
