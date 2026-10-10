package ma.teamslot.match.application;

import java.util.List;
import java.util.UUID;
import ma.teamslot.match.application.VenueClient.VenueReservation;
import ma.teamslot.match.domain.Match;
import ma.teamslot.match.events.Outbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CreateMatchService {

    private static final Logger log = LoggerFactory.getLogger(CreateMatchService.class);

    private static final String KIND = "FRIENDLY";
    private static final String STATUS = "PENDING_PAYMENT";
    private static final String VISIBILITE = "PRIVATE"; // à confirmer dans match.yaml (MatchVisibility)

    private final VenueClient venue;
    private final MatchRepository matches;
    private final Outbox outbox;
    private final TransactionTemplate transaction;
    private final TransactionTemplate compensation;

    public CreateMatchService(VenueClient venue, MatchRepository matches, Outbox outbox,
                              PlatformTransactionManager transactionManager) {
        this.venue = venue;
        this.matches = matches;
        this.outbox = outbox;
        this.transaction = new TransactionTemplate(transactionManager);
        this.compensation = new TransactionTemplate(transactionManager);
        this.compensation.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Pas de transaction autour de l'appel à venue : une connexion à la base resterait bloquée si
     * venue est lent. Seuls l'enregistrement du match et l'écriture dans l'outbox partagent
     * la même transaction.
     */
    public Match create(UUID callerId, UUID slotId, List<UUID> equipmentIds) {
        UUID matchId = UUID.randomUUID();
        List<UUID> equipment = equipmentIds == null ? List.of() : List.copyOf(equipmentIds);

        VenueReservation reservation = venue.reserve(matchId, slotId, equipment);
        try {
            return transaction.execute(status -> enregistrer(matchId, callerId, slotId, reservation));
        } catch (RuntimeException e) {
            annulerSansMasquerLErreur(matchId, callerId);
            throw e;
        }
    }

    private Match enregistrer(UUID matchId, UUID callerId, UUID slotId, VenueReservation r) {
        Match match = new Match(matchId, KIND, STATUS, VISIBILITE, 0, callerId, callerId,
                r.venueId(), slotId, r.startsAt(), r.totalAmountCents(), r.currency(),
                r.acceptsOnSitePayment(), 0L);
        matches.save(match);
        outbox.record(matchId, "match.created", new MatchCreatedData(matchId, KIND, VISIBILITE,
                callerId, callerId, r.venueId(), slotId, r.startsAt(), r.totalAmountCents(),
                r.currency(), r.acceptsOnSitePayment()));
        return match;
    }

    /**
     * Compensation (décision SCRUM-25) : venue libère le créneau en consommant match.cancelled.
     * Transaction séparée, car celle de l'enregistrement est annulée.
     * Si l'écriture échoue aussi, on trace l'erreur sans masquer l'erreur d'origine.
     */
    private void annulerSansMasquerLErreur(UUID matchId, UUID callerId) {
        try {
            compensation.executeWithoutResult(status -> outbox.record(matchId, "match.cancelled",
                    new MatchCancelledData(matchId, "creation_failed", callerId)));
        } catch (RuntimeException e) {
            log.error("match.cancelled non écrit : créneau possiblement bloqué, match {}", matchId, e);
        }
    }
}
