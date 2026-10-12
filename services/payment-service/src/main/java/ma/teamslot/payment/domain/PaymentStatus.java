package ma.teamslot.payment.domain;

/** Statuts du contrat (docs/api/payment.yaml, PaymentStatus). */
public enum PaymentStatus {
    PENDING, COMPLETED, ON_SITE_ACCEPTED, COLLECTED, FAILED, REFUNDED
}
