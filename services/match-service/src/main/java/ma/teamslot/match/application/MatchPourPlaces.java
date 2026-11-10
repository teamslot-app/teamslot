package ma.teamslot.match.application;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Ce qu'il faut savoir d'un match pour y ouvrir des places (SCRUM-78).
 * version sert à l'écriture conditionnelle : si un participant est ajouté entre la lecture et l'écriture,
 * la version a changé et rien n'est écrit (le calcul des places ne repose jamais sur une liste périmée).
 */
public record MatchPourPlaces(UUID organisateur, String statut, int maxPlayers, int joueursInscrits, long version) {

    /** Même règle que l'ajout de participants (MatchPourAjout) : avant le match seulement. */
    static final Set<String> STATUTS_OUVERTS = Set.of("PENDING_PAYMENT", "CONFIRMED");

    /** Un participant qui occupe une place : invité par l'organisateur ou confirmé (pas REQUESTED, pas DECLINED). */
    static final Set<String> STATUTS_QUI_JOUENT = Set.of("INVITED", "CONFIRMED");

    /** Un participant tel que stocké : compte (userId) ou invité (userId null), et son statut. */
    public record Inscrit(UUID userId, String statut) {
    }

    /**
     * Joueurs inscrits = organisateur + participants INVITED ou CONFIRMED.
     * L'organisateur n'est dans le tableau qu'après une répartition (SplitTeamsService) :
     * il n'est compté qu'une fois.
     */
    public static MatchPourPlaces de(UUID organisateur, String statut, int maxPlayers, long version,
                                     List<Inscrit> participants) {
        int joueurs = (int) participants.stream()
                .filter(p -> STATUTS_QUI_JOUENT.contains(p.statut()))
                .filter(p -> !organisateur.equals(p.userId()))
                .count();
        return new MatchPourPlaces(organisateur, statut, maxPlayers, joueurs + 1, version);
    }

    public boolean ouvert() {
        return STATUTS_OUVERTS.contains(statut);
    }

    /** Places qu'on peut encore ouvrir sans dépasser maxPlayers (jamais négatif). */
    public int placesPossibles() {
        return Math.max(0, maxPlayers - joueursInscrits);
    }

    public boolean estOrganisateur(UUID appelant) {
        return Objects.equals(organisateur, appelant);
    }
}
