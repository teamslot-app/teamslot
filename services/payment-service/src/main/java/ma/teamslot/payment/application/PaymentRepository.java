package ma.teamslot.payment.application;

import ma.teamslot.payment.domain.Payment;

/** Port de sortie : stockage des paiements. */
public interface PaymentRepository {

    /** Enregistre le paiement s'il n'en existe pas deja un pour ce match (S6). @return true si cree. */
    boolean creerSiAbsent(Payment payment);
}
