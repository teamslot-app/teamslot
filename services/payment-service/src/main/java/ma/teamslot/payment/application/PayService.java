package ma.teamslot.payment.application;

import java.util.UUID;
import ma.teamslot.payment.domain.Payment;
import ma.teamslot.payment.events.Outbox;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * SCRUM-62 : le responsable du paiement paie en ligne (simule) ou choisit le paiement sur place.
 * Le montant est celui enregistre a la reception de match.created : le client n'envoie aucun montant.
 * Verrou de la ligne, changement d'etat et outbox dans la meme transaction (S6 : un seul paiement).
 */
@Service
public class PayService {

    static final String PAYMENT_COMPLETED = "payment.completed";
    static final String PAYMENT_ON_SITE_ACCEPTED = "payment.on_site_accepted";

    private final PaymentRepository payments;
    private final Outbox outbox;
    private final TransactionTemplate transaction;

    public PayService(PaymentRepository payments, Outbox outbox, PlatformTransactionManager transactionManager) {
        this.payments = payments;
        this.outbox = outbox;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public Payment payerEnLigne(UUID appelant, UUID matchId) {
        return transaction.execute(status -> {
            Payment paye = verrouiller(matchId).payerEnLigne(appelant);
            payments.mettreAJour(paye);
            outbox.record(matchId, PAYMENT_COMPLETED, new PaymentEventData.Completed(matchId, paye.id(),
                    paye.payerId(), paye.mode().name(), paye.totalAmountCents(), paye.currency()));
            return paye;
        });
    }

    public Payment choisirSurPlace(UUID appelant, UUID matchId) {
        return transaction.execute(status -> {
            Payment accepte = verrouiller(matchId).choisirSurPlace(appelant);
            payments.mettreAJour(accepte);
            outbox.record(matchId, PAYMENT_ON_SITE_ACCEPTED, new PaymentEventData.OnSiteAccepted(matchId,
                    accepte.id(), accepte.payerId(), accepte.totalAmountCents(), accepte.currency()));
            return accepte;
        });
    }

    public Payment consulter(UUID appelant, UUID matchId) {
        Payment payment = payments.trouver(matchId).orElseThrow(() -> new PaymentNotFoundException(matchId));
        payment.verifierLecture(appelant);
        return payment;
    }

    private Payment verrouiller(UUID matchId) {
        return payments.trouverPourMiseAJour(matchId).orElseThrow(() -> new PaymentNotFoundException(matchId));
    }
}
