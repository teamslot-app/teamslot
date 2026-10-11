package ma.teamslot.match.application;

import java.util.UUID;

/** Match inexistant : reponse 404. */
public class MatchNotFoundException extends RuntimeException {
    public MatchNotFoundException(UUID matchId) {
        super("Match introuvable : " + matchId);
    }
}
