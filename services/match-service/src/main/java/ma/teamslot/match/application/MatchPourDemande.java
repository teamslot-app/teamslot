package ma.teamslot.match.application;

import java.util.Set;
import java.util.UUID;

/**
 * Ce qu'il faut savoir d'un match pour y demander une place ou accepter une demande (SCRUM-79).
 * comptes : userId déjà dans le tableau participants (quel que soit leur statut) ; demandeurs : demandes en attente.
 */
public record MatchPourDemande(UUID organisateur, String statut, int openSpots, Set<UUID> comptes,
                               Set<UUID> demandeurs) {

    /** Même règle que l'ajout de participants et l'ouverture de places : avant le match seulement. */
    public boolean ouvert() {
        return MatchPourPlaces.STATUTS_OUVERTS.contains(statut);
    }

    public boolean estOrganisateur(UUID appelant) {
        return organisateur.equals(appelant);
    }
}
