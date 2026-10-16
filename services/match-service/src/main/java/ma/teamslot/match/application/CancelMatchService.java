package ma.teamslot.match.application;

import java.util.UUID;
import ma.teamslot.events.Outbox;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * SCRUM-76 : l'organisateur annule son match. Le match passe à CANCELLED et match.cancelled est publié :
 * venue-service libère le créneau (slot.released) et payment-service rembourse un paiement en ligne
 * (payment.refunded). Aucun appel synchrone vers venue ou payment (docs/events.md, section 5).
 */
@Service
public class CancelMatchService {

    static final String EVENEMENT = "match.cancelled";
    static final String RAISON = "ORGANIZER_CANCELLED";

    private final MatchRepository matches;
    private final Outbox outbox;
    private final TransactionTemplate transaction;

    public CancelMatchService(MatchRepository matches, Outbox outbox, PlatformTransactionManager transactionManager) {
        this.matches = matches;
        this.outbox = outbox;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Ordre : 404 match introuvable, 403 pas organisateur, 409 match plus annulable (joué, déjà annulé...).
     * Changement d'état et outbox dans la même transaction MongoDB.
     */
    public void cancel(UUID appelant, UUID matchId) {
        transaction.executeWithoutResult(status -> {
            MatchPourAnnulation match = matches.pourAnnulation(matchId)
                    .orElseThrow(() -> new MatchNotFoundException(matchId));
            if (!match.organisateur().equals(appelant)) {
                throw new NotMatchOrganizerException();
            }
            // annuler() est conditionnel : si le statut a changé entre-temps, rien n'est écrit.
            if (!match.annulable() || !matches.annuler(matchId)) {
                throw new MatchNotCancellableException(match.statut());
            }
            outbox.record(matchId, EVENEMENT, new MatchCancelledData(matchId, RAISON, appelant));
        });
    }
}
