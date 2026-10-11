package ma.teamslot.match.application;

import java.util.Optional;
import java.util.UUID;
import ma.teamslot.match.domain.Participant;

/** Port de sortie : lecture du match et enregistrement des participants. */
public interface ParticipantRepository {

    /**
     * Organisateur et statut du match, vide si le match n'existe pas.
     * Le match reste verrouille en lecture jusqu'a la fin de la transaction : il ne peut pas etre
     * annule pendant l'ajout.
     */
    Optional<MatchPourAjout> matchPourAjout(UUID matchId);

    /** Enregistre le participant ; ParticipantAlreadyAddedException si le compte ou le surnom est deja pris. */
    void save(Participant participant, UUID ajoutePar);
}
