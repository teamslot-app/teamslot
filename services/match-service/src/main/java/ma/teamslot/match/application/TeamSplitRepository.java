package ma.teamslot.match.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ma.teamslot.match.domain.Participant;

/** Port de sortie de SCRUM-61 : répartition en équipes et liste des participants. */
public interface TeamSplitRepository {

    /** Organisateur et statut du match, verrouillé (FOR UPDATE) jusqu'à la fin de la transaction ; vide s'il n'existe pas. */
    Optional<MatchPourRepartition> verrouillerMatch(UUID matchId);

    /** Inscrit l'organisateur comme participant CONFIRMED, sans effet s'il l'est déjà (jamais deux fois). */
    void inscrireOrganisateur(UUID matchId, UUID organisateur);

    /** Participants à tirer : INVITED et CONFIRMED (ni REQUESTED, ni DECLINED). */
    List<Participant> joueurs(UUID matchId);

    /** Remplace les côtés précédents : tous remis à null, puis A et B posés. */
    void enregistrerCotes(UUID matchId, List<UUID> coteA, List<UUID> coteB);

    Optional<UUID> organisateur(UUID matchId);

    /** Vrai si ce compte participe au match (INVITED ou CONFIRMED). */
    boolean aUnCompteParticipant(UUID matchId, UUID userId);

    List<ParticipantAvecCote> participants(UUID matchId);
}
