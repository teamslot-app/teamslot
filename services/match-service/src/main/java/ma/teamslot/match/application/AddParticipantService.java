package ma.teamslot.match.application;

import java.util.UUID;
import ma.teamslot.match.domain.InvalidParticipantException;
import ma.teamslot.match.domain.Participant;
import ma.teamslot.events.Outbox;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** SCRUM-60 : l'organisateur ajoute un ami, avec un compte ou un simple surnom. */
@Service
public class AddParticipantService {

    static final String EVENEMENT = "match.participant_added";

    private final ParticipantRepository participants;
    private final Outbox outbox;
    private final TransactionTemplate transaction;

    public AddParticipantService(ParticipantRepository participants, Outbox outbox,
                                 PlatformTransactionManager transactionManager) {
        this.participants = participants;
        this.outbox = outbox;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Exactement un des deux : userId (compte) ou guestName (invite sans compte).
     * Ordre : 404 match introuvable, 403 pas organisateur, 409 match ferme, 409 organisateur ou deja present.
     * Verification, enregistrement et outbox dans la meme transaction.
     */
    public Participant add(UUID appelant, UUID matchId, UUID userId, String guestName) {
        if ((userId == null) == (guestName == null)) {
            throw new InvalidParticipantException("Fournir soit userId, soit guestName, jamais les deux.");
        }
        Participant participant = userId != null
                ? Participant.compte(matchId, userId)
                : Participant.invite(matchId, guestName);

        return transaction.execute(status -> {
            MatchPourAjout match = participants.matchPourAjout(matchId)
                    .orElseThrow(() -> new MatchNotFoundException(matchId));
            if (!match.organisateur().equals(appelant)) {
                throw new NotMatchOrganizerException();
            }
            if (!match.ouvertAuxAjouts()) {
                throw new MatchClosedException(match.statut());
            }
            if (match.organisateur().equals(userId)) {
                throw new ParticipantAlreadyAddedException(ParticipantAlreadyAddedException.Raison.ORGANISATEUR, null);
            }
            participants.save(participant, appelant);
            outbox.record(matchId, EVENEMENT, new ParticipantAddedData(matchId, participant.id(),
                    participant.userId(), participant.guestName()));
            return participant;
        });
    }
}
