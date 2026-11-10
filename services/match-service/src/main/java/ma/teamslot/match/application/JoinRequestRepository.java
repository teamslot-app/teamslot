package ma.teamslot.match.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ma.teamslot.match.domain.Participant;

/** Port de sortie de SCRUM-79 : demandes pour rejoindre un match (tableau joinRequests du document matches). */
public interface JoinRequestRepository {

    /** Organisateur, statut, places, comptes participants et demandeurs ; vide si le match n'existe pas. */
    Optional<MatchPourDemande> pourDemande(UUID matchId);

    /**
     * Ajoute la demande en une écriture conditionnelle : match ouvert, au moins une place, joueur ni organisateur,
     * ni participant, ni déjà demandeur. Ne change pas la version : une demande ne change pas les places.
     * @return false si la condition n'est plus vraie (rien n'a été écrit).
     */
    boolean demander(UUID matchId, UUID joueur, Instant le);

    /** Demandes en attente, par ordre d'arrivée. */
    List<DemandeRejoindre> demandes(UUID matchId);

    /**
     * Retire la demande, ajoute le participant et fait openSpots - 1 (version + 1) en UNE écriture conditionnelle
     * (organisateur, statut ouvert, openSpots > 0, demande présente) : deux acceptations simultanées
     * ne dépassent jamais le nombre de places.
     * @return false si la condition n'est plus vraie (rien n'a été écrit).
     */
    boolean accepter(UUID matchId, UUID organisateur, Participant joueur);
}
