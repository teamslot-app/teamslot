package ma.teamslot.payment.application;

import java.util.UUID;
import ma.teamslot.payment.domain.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** SCRUM-62 : un match cree attend son paiement (statut PENDING). */
@Service
public class CreatePendingPaymentService {

    private static final Logger LOG = LoggerFactory.getLogger(CreatePendingPaymentService.class);

    private final PaymentRepository payments;

    public CreatePendingPaymentService(PaymentRepository payments) {
        this.payments = payments;
    }

    /** A appeler dans la transaction du consommateur idempotent. */
    public void surMatchCree(UUID matchId, UUID payerId, long totalAmountCents, String currency,
                             boolean acceptsOnSitePayment) {
        Payment payment = Payment.enAttente(matchId, payerId, totalAmountCents, currency, acceptsOnSitePayment);
        if (!payments.creerSiAbsent(payment)) {
            LOG.info("Paiement deja present pour le match {}, rien a faire", matchId);
        }
    }
}
