package ma.teamslot.match.application;

import java.util.UUID;
import ma.teamslot.events.Outbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * SCRUM-62 : le paiement est fait (en ligne) ou accepte sur place, le match passe en CONFIRMED
 * et match.confirmed est publie. A appeler dans la transaction du consommateur idempotent.
 */
@Service
public class ConfirmMatchService {

    static final String MATCH_CONFIRMED = "match.confirmed";
    private static final Logger LOG = LoggerFactory.getLogger(ConfirmMatchService.class);

    private final MatchRepository matches;
    private final Outbox outbox;

    public ConfirmMatchService(MatchRepository matches, Outbox outbox) {
        this.matches = matches;
        this.outbox = outbox;
    }

    public void surPaiement(UUID matchId, String typePaiement) {
        if (matches.confirmer(matchId)) {
            outbox.record(matchId, MATCH_CONFIRMED, new MatchConfirmedData(matchId));
        } else {
            // Match inconnu ou deja confirme : rien a faire, le paiement reste trace chez payment.
            LOG.info("{} ignore pour le match {} : inconnu ou pas en attente de paiement", typePaiement, matchId);
        }
    }
}
