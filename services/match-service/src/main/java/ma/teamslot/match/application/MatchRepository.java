package ma.teamslot.match.application;

import java.util.UUID;
import ma.teamslot.match.domain.Match;

public interface MatchRepository {
    void save(Match match);

    /**
     * SCRUM-62 : PENDING_PAYMENT -> CONFIRMED (version + 1).
     * @return true si le match a change, false s'il n'existe pas ou n'attendait plus le paiement.
     */
    boolean confirmer(UUID matchId);
}
