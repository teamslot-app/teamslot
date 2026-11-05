package ma.teamslot.payment.domain;

/** SCRUM-75 : seul un paiement ON_SITE_ACCEPTED peut être encaissé (déjà encaissé, en ligne, en attente) : 409. */
public class PaymentNotOnSiteAcceptedException extends RuntimeException {
    public PaymentNotOnSiteAcceptedException(PaymentStatus statut) {
        super("Seul un paiement sur place accepté peut être encaissé : " + statut);
    }
}
