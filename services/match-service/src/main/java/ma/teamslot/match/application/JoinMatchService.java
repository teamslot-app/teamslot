package ma.teamslot.match.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import ma.teamslot.events.Outbox;
import ma.teamslot.match.application.ParticipantAlreadyAddedException.Raison;
import ma.teamslot.match.domain.Participant;
import ma.teamslot.match.domain.ParticipantStatus;
import org.springframework.stereotype.Service;

/**
 * SCRUM-79 : un joueur demande une place dans un match aux places ouvertes ; l'organisateur accepte.
 * Chaque changement et son événement (outbox) sont dans la même transaction.
 */
@Service
public class JoinMatchService {

    static final String DEMANDE = "match.join_requested";
    static final String ACCEPTATION = "match.join_accepted";

    private final JoinRequestRepository repository;
    private final Outbox outbox;
    private final Transactions transactions;
    private final Clock horloge;

    public JoinMatchService(JoinRequestRepository repository, Outbox outbox, Transactions transactions, Clock horloge) {
        this.repository = repository;
        this.outbox = outbox;
        this.transactions = transactions;
        this.horloge = horloge;
    }

    /**
     * Ordre : 404 match introuvable, 409 match fermé, 409 organisateur ou déjà participant, 409 déjà demandé,
     * 409 plus de place. L'identité vient du jeton : un joueur ne demande que pour lui-même.
     */
    public DemandeRejoindre demander(UUID joueur, UUID matchId) {
        return transactions.executer(() -> {
            MatchPourDemande match = repository.pourDemande(matchId).orElseThrow(() -> new MatchNotFoundException(matchId));
            if (!match.ouvert()) {
                throw new MatchClosedException(match.statut());
            }
            if (match.estOrganisateur(joueur)) {
                throw new ParticipantAlreadyAddedException(Raison.ORGANISATEUR, null);
            }
            if (match.comptes().contains(joueur)) {
                throw new ParticipantAlreadyAddedException(Raison.COMPTE, null);
            }
            if (match.demandeurs().contains(joueur)) {
                throw new JoinAlreadyRequestedException();
            }
            if (match.openSpots() < 1) {
                throw new NoOpenSpotsException();
            }
            DemandeRejoindre demande = new DemandeRejoindre(matchId, joueur, horloge.instant());
            if (!repository.demander(matchId, joueur, demande.requestedAt())) {
                throw new MatchChangedException();
            }
            outbox.record(matchId, DEMANDE, new JoinRequestedData(matchId, joueur));
            return demande;
        });
    }

    /** Réservé à l'organisateur : 404 match introuvable, 403 sinon. */
    public List<DemandeRejoindre> demandes(UUID appelant, UUID matchId) {
        MatchPourDemande match = repository.pourDemande(matchId).orElseThrow(() -> new MatchNotFoundException(matchId));
        if (!match.estOrganisateur(appelant)) {
            throw new NotMatchOrganizerException();
        }
        return repository.demandes(matchId);
    }

    /**
     * Ordre : 404 match introuvable, 403 pas organisateur, 409 match fermé, 404 pas de demande de ce joueur,
     * 409 déjà participant, 409 plus de place. Le joueur accepté est CONFIRMED : c'est lui qui a demandé.
     */
    public Participant accepter(UUID appelant, UUID matchId, UUID joueur) {
        return transactions.executer(() -> {
            MatchPourDemande match = repository.pourDemande(matchId).orElseThrow(() -> new MatchNotFoundException(matchId));
            if (!match.estOrganisateur(appelant)) {
                throw new NotMatchOrganizerException();
            }
            if (!match.ouvert()) {
                throw new MatchClosedException(match.statut());
            }
            if (!match.demandeurs().contains(joueur)) {
                throw new JoinRequestNotFoundException();
            }
            if (match.comptes().contains(joueur)) {
                throw new ParticipantAlreadyAddedException(Raison.COMPTE, null);
            }
            if (match.openSpots() < 1) {
                throw new NoOpenSpotsException();
            }
            Participant participant = new Participant(UUID.randomUUID(), matchId, joueur, null,
                    ParticipantStatus.CONFIRMED);
            // Conditionnelle sur openSpots > 0 et la demande : rien n'est écrit si un autre l'a pris entre-temps.
            if (!repository.accepter(matchId, appelant, participant)) {
                throw new MatchChangedException();
            }
            outbox.record(matchId, ACCEPTATION, new JoinAcceptedData(matchId, joueur));
            return participant;
        });
    }
}
