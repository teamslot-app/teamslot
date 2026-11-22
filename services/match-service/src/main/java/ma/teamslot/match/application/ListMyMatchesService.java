package ma.teamslot.match.application;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import ma.teamslot.match.domain.Match;
import org.springframework.stereotype.Service;

/**
 * SCRUM-77 : mes matchs à venir et le détail d'un match (docs/api/match.yaml, listMyMatches et getMatch).
 * L'appelant vient toujours du JWT (Appelant) : aucun paramètre ne permet de lire les matchs d'un autre joueur.
 */
@Service
public class ListMyMatchesService {

    /** Un créneau dure une heure : un match reste « à venir » jusqu'à la fin de son créneau. */
    static final Duration DUREE_CRENEAU = Duration.ofHours(1);
    static final int LIMITE = 50;

    private final MatchRepository matches;
    private final Clock horloge;

    public ListMyMatchesService(MatchRepository matches, Clock horloge) {
        this.matches = matches;
        this.horloge = horloge;
    }

    /** Organisateur ou participant avec compte, créneau pas terminé, annulés compris, par heure de début. */
    public List<Match> aVenir(UUID appelant) {
        return matches.mesMatchs(appelant, horloge.instant().minus(DUREE_CRENEAU), LIMITE);
    }

    /**
     * Même réponse (404) pour un match inexistant et pour le match d'un autre joueur :
     * on ne révèle pas l'existence d'un match à quelqu'un qui n'y participe pas.
     */
    public Match detail(UUID appelant, UUID matchId) {
        return matches.pourParticipant(matchId, appelant).orElseThrow(() -> new MatchNotFoundException(matchId));
    }
}
