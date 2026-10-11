package ma.teamslot.match.application;

import java.util.Optional;
import java.util.UUID;
import ma.teamslot.match.domain.Participant;

/** Port de sortie : lecture de l'organisateur d'un match et enregistrement des participants. */
public interface ParticipantRepository {

    /** Organisateur principal (owner_id) du match, vide si le match n'existe pas. */
    Optional<UUID> organisateurDe(UUID matchId);

    /** Enregistre le participant ; ParticipantAlreadyAddedException si ce compte est deja dans le match. */
    void save(Participant participant, UUID ajoutePar);
}
