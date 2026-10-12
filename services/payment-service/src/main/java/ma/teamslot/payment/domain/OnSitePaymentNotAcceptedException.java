package ma.teamslot.payment.domain;

/** Le terrain n'accepte pas le paiement sur place : reponse 409. */
public class OnSitePaymentNotAcceptedException extends RuntimeException {
    public OnSitePaymentNotAcceptedException() {
        super("Ce terrain n'accepte pas le paiement sur place.");
    }
}
