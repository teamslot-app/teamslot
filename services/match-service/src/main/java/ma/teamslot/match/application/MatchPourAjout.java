package ma.teamslot.match.application;

import java.util.Set;
import java.util.UUID;

/** Ce qu'il faut savoir d'un match pour y ajouter un participant (SCRUM-60). */
public record MatchPourAjout(UUID organisateur, String statut) {

    /** Decision PO : ajout possible seulement avant le match (en attente de paiement ou confirme). */
    private static final Set<String> STATUTS_OUVERTS = Set.of("PENDING_PAYMENT", "CONFIRMED");

    public boolean ouvertAuxAjouts() {
        return STATUTS_OUVERTS.contains(statut);
    }
}
