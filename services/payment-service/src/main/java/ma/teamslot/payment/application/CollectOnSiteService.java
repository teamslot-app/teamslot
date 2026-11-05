package ma.teamslot.payment.application;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import ma.teamslot.events.Outbox;
import ma.teamslot.payment.domain.Payment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * SCRUM-75 : le gérant du terrain confirme qu'un groupe a payé sur place.
 * - identité (sub) et rôle (claim role) viennent du JWT vérifié, jamais de la requête ;
 * - refus par défaut (403, test S14) : joueur, gérant d'un autre terrain, jeton sans rôle, terrain inconnu ;
 * - seul un paiement ON_SITE_ACCEPTED passe à COLLECTED (sinon 409), ligne verrouillée (FOR UPDATE) ;
 * - payment.collected_on_site est publié par l'outbox, dans la même transaction que le changement d'état.
 */
@Service
public class CollectOnSiteService {

    static final String PAYMENT_COLLECTED_ON_SITE = "payment.collected_on_site";
    static final String ROLE_GERANT = "VENUE_MANAGER";

    private final PaymentRepository payments;
    private final VenueOwnerRepository gerants;
    private final Outbox outbox;
    private final TransactionTemplate transaction;
    private final Clock horloge;

    public CollectOnSiteService(PaymentRepository payments, VenueOwnerRepository gerants, Outbox outbox,
                                PlatformTransactionManager transactionManager) {
        this.payments = payments;
        this.gerants = gerants;
        this.outbox = outbox;
        this.transaction = new TransactionTemplate(transactionManager);
        this.horloge = Clock.systemUTC();
    }

    public Payment encaisser(UUID appelant, String role, UUID matchId) {
        return transaction.execute(status -> {
            Payment payment = payments.trouverPourMiseAJour(matchId)
                    .orElseThrow(() -> new PaymentNotFoundException(matchId));
            // Le droit est vérifié avant l'état : un autre gérant reçoit 403 même sur un paiement déjà encaissé.
            if (!ROLE_GERANT.equals(role) || payment.venueId() == null
                    || !gerants.gerant(payment.venueId()).map(appelant::equals).orElse(false)) {
                throw new NotVenueManagerException();
            }
            // Précision de PostgreSQL (microseconde) : la réponse et la base donnent la même heure.
            Payment encaisse = payment.encaisserSurPlace(appelant, horloge.instant().truncatedTo(ChronoUnit.MICROS));
            payments.mettreAJour(encaisse);
            outbox.record(matchId, PAYMENT_COLLECTED_ON_SITE, new PaymentEventData.CollectedOnSite(matchId,
                    encaisse.id(), encaisse.collectedBy(), encaisse.collectedAt().toString()));
            return encaisse;
        });
    }
}
