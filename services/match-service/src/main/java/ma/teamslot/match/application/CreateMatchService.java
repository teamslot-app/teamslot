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
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CreateMatchService {

    private static final Logger log = LoggerFactory.getLogger(CreateMatchService.class);

    private static final String KIND = "FRIENDLY";
    private static final String STATUS = "PENDING_PAYMENT";
    private static final String VISIBILITE = "PRIVATE"; // confirmé par Laila pour un match FRIENDLY

    private final VenueClient venue;
    private final MatchRepository matches;
    private final Outbox outbox;
    private final TransactionTemplate transaction;

    public CreateMatchService(VenueClient venue, MatchRepository matches, Outbox outbox,
                              PlatformTransactionManager transactionManager) {
        this.venue = venue;
        this.matches = matches;
        this.outbox = outbox;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Pas de transaction autour de l'appel à venue : une connexion à la base resterait bloquée si
     * venue est lent. Seuls l'enregistrement du match et l'écriture dans l'outbox partagent
     * la même transaction.
     */
    public Match create(UUID callerId, String bearerToken, UUID slotId, List<UUID> equipmentIds) {
        UUID matchId = UUID.randomUUID();
        List<UUID> equipment = equipmentIds == null ? List.of() : List.copyOf(equipmentIds);

        VenueReservation reservation = venue.reserve(matchId, slotId, equipment, bearerToken);
        try {
            return transaction.execute(status -> enregistrer(matchId, callerId, slotId, reservation));
        } catch (RuntimeException e) {
            // Compensation : venue libère le créneau en consommant match.cancelled, prévu dans
            // SCRUM-76 (topic et consommateur absents pour l'instant). On trace pour pouvoir agir.
            log.error("Créneau réservé mais match non enregistré : matchId={} slotId={} (à libérer, SCRUM-76)",
                    matchId, slotId, e);
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
}
