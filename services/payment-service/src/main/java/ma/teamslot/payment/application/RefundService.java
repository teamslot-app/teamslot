package ma.teamslot.payment.application;

import java.util.Optional;
import java.util.UUID;
import ma.teamslot.events.Outbox;
import ma.teamslot.payment.domain.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * SCRUM-76 : un match annulé (match.cancelled) déclenche le remboursement d'un paiement en ligne.
 * A appeler dans la transaction du consommateur idempotent : un événement rejoué ne rembourse qu'une fois (S6).
 * Ligne verrouillée (FOR UPDATE) comme pour le paiement (SCRUM-62) ; montant lu en base, jamais dans l'événement.
 */
@Service
public class RefundService {

    static final String PAYMENT_REFUNDED = "payment.refunded";
    private static final Logger LOG = LoggerFactory.getLogger(RefundService.class);

    private final PaymentRepository payments;
    private final Outbox outbox;

    public RefundService(PaymentRepository payments, Outbox outbox) {
        this.payments = payments;
        this.outbox = outbox;
    }

    public void surMatchAnnule(UUID matchId) {
        Optional<Payment> trouve = payments.trouverPourMiseAJour(matchId);
        if (trouve.isEmpty()) {
            LOG.info("match.cancelled pour le match {} : aucun paiement, rien a rembourser", matchId);
            return;
        }
        Payment payment = trouve.get();
        if (!payment.remboursable()) {
            // PENDING ou ON_SITE_ACCEPTED : rien n'a été encaissé en ligne, rien à rembourser.
            LOG.info("match.cancelled pour le match {} : paiement {} non rembourse (statut {})",
                    matchId, payment.id(), payment.status());
            return;
        }
        Payment rembourse = payment.rembourser();
        payments.mettreAJour(rembourse);
        outbox.record(matchId, PAYMENT_REFUNDED, new PaymentEventData.Refunded(matchId, rembourse.id(),
                rembourse.totalAmountCents(), rembourse.currency()));
    }
}
