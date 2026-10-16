package ma.teamslot.match.application;

import java.util.Optional;
import java.util.UUID;
import ma.teamslot.match.domain.Match;

public interface MatchRepository {
    void save(Match match);

    /**
     * SCRUM-62 : PENDING_PAYMENT -> CONFIRMED (version + 1).
     * @return true si le match a change, false s'il n'existe pas ou n'attendait plus le paiement.
     */
    boolean confirmer(UUID matchId);

    /** SCRUM-76 : organisateur et statut du match, vide s'il n'existe pas. */
    Optional<MatchPourAnnulation> pourAnnulation(UUID matchId);

    /**
     * SCRUM-76 : PENDING_PAYMENT ou CONFIRMED -> CANCELLED (version + 1), en une écriture conditionnelle.
     * @return true si le match a été annulé, false s'il n'était plus annulable.
     */
    boolean annuler(UUID matchId);
}
