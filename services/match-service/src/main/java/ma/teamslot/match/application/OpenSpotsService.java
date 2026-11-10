package ma.teamslot.match.application;

import java.util.UUID;
import ma.teamslot.events.Outbox;
import ma.teamslot.match.domain.Match;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * SCRUM-78 : l'organisateur ouvre des places à des joueurs extérieurs pour compléter son groupe.
 * openSpots est fixé (il ne s'ajoute pas) ; match.spots_opened est publié par l'outbox.
 */
@Service
public class OpenSpotsService {

    static final String EVENEMENT = "match.spots_opened";

    private final MatchRepository matches;
    private final Outbox outbox;
    private final TransactionTemplate transaction;

    public OpenSpotsService(MatchRepository matches, Outbox outbox, PlatformTransactionManager transactionManager) {
        this.matches = matches;
        this.outbox = outbox;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Ordre : 404 match introuvable, 403 pas organisateur, 409 match fermé, 400 nombre de places refusé,
     * 409 match modifié entre-temps. Écriture conditionnelle et outbox dans la même transaction MongoDB.
     */
    public Match open(UUID appelant, UUID matchId, int openSpots) {
        return transaction.execute(status -> {
            MatchPourPlaces match = matches.pourPlaces(matchId)
                    .orElseThrow(() -> new MatchNotFoundException(matchId));
            if (!match.estOrganisateur(appelant)) {
                throw new NotMatchOrganizerException();
            }
            if (!match.ouvert()) {
                throw new MatchClosedException(match.statut());
            }
            // Moins de 1 est déjà refusé par le contrôleur (@Min(1)) ; règle gardée ici pour les autres appelants.
            if (openSpots < 1 || openSpots > match.placesPossibles()) {
                throw new InvalidOpenSpotsException(match.placesPossibles());
            }
            // Conditionnelle sur organisateur, statut et version : rien n'est écrit si le match a changé.
            Match ouvert = matches.ouvrirPlaces(matchId, appelant, match.version(), openSpots)
                    .orElseThrow(MatchChangedException::new);
            outbox.record(matchId, EVENEMENT,
                    new SpotsOpenedData(matchId, ouvert.venueId(), ouvert.startsAt(), ouvert.openSpots()));
            return ouvert;
        });
    }
}
