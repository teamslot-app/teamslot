package ma.teamslot.match.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** SCRUM-61 : liste des participants, avec leur côté après un tirage. */
@Service
public class ListParticipantsService {

    private final TeamSplitRepository repository;

    public ListParticipantsService(TeamSplitRepository repository) {
        this.repository = repository;
    }

    /** 404 match introuvable ; 403 sauf pour l'organisateur et les participants avec compte (INVITED ou CONFIRMED). */
    public List<ParticipantAvecCote> list(UUID appelant, UUID matchId) {
        UUID organisateur = repository.organisateur(matchId)
                .orElseThrow(() -> new MatchNotFoundException(matchId));
        if (!organisateur.equals(appelant) && !repository.aUnCompteParticipant(matchId, appelant)) {
            throw new NotAParticipantException();
        }
        return repository.participants(matchId);
    }
}
