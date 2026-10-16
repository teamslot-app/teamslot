package ma.teamslot.match.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import ma.teamslot.match.domain.Participant;
import ma.teamslot.match.domain.TeamSplit;
import ma.teamslot.match.domain.TeamSplitStrategy;
import ma.teamslot.events.Outbox;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** SCRUM-61 : l'organisateur répartit les joueurs en deux équipes ; un nouveau tirage remplace le précédent. */
@Service
public class SplitTeamsService {

    static final String EVENEMENT = "match.teams_split";
    private static final String STATUT_REQUIS = "CONFIRMED";
    private static final int JOUEURS_MIN = 2;

    private final TeamSplitRepository repository;
    private final Map<String, TeamSplitStrategy> strategies;
    private final Outbox outbox;
    private final TransactionTemplate transaction;

    public SplitTeamsService(TeamSplitRepository repository, List<TeamSplitStrategy> strategies,
                             Outbox outbox, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.strategies = strategies.stream()
                .collect(Collectors.toMap(TeamSplitStrategy::name, Function.identity()));
        this.outbox = outbox;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Ordre : 400 stratégie inconnue, 404 match introuvable, 403 pas organisateur, 409 match pas CONFIRMED,
     * 409 moins de deux joueurs. Inscription de l'organisateur, côtés et outbox : une seule transaction
     * (un refus annule aussi l'inscription de l'organisateur).
     */
    public TeamSplit split(UUID appelant, UUID matchId, String nomStrategie) {
        TeamSplitStrategy strategie = strategies.get(nomStrategie);
        if (strategie == null) {
            throw new UnknownStrategyException(strategies.keySet());
        }
        return transaction.execute(status -> {
            MatchPourRepartition match = repository.verrouillerMatch(matchId)
                    .orElseThrow(() -> new MatchNotFoundException(matchId));
            if (!match.organisateur().equals(appelant)) {
                throw new NotMatchOrganizerException();
            }
            if (!STATUT_REQUIS.equals(match.statut())) {
                throw new MatchNotConfirmedException();
            }
            repository.inscrireOrganisateur(matchId, match.organisateur());
            List<Participant> joueurs = repository.joueurs(matchId);
            if (joueurs.size() < JOUEURS_MIN) {
                throw new NotEnoughPlayersException();
            }
            TeamSplit split = strategie.split(joueurs);
            repository.enregistrerCotes(matchId, ids(split.sideA()), ids(split.sideB()));
            outbox.record(matchId, EVENEMENT, TeamSplitData.de(matchId, strategie.name(), split));
            return split;
        });
    }

    private static List<UUID> ids(List<Participant> joueurs) {
        return joueurs.stream().map(Participant::id).toList();
    }
}
