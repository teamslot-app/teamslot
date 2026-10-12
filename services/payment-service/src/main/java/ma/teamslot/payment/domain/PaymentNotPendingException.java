package ma.teamslot.payment.domain;

/** Le paiement n'est plus en attente (deja paye ou mode deja choisi) : reponse 409 (S6). */
public class PaymentNotPendingException extends RuntimeException {
    public PaymentNotPendingException(PaymentStatus statut) {
        super("Le paiement n'est plus en attente : " + statut);
    }
}
