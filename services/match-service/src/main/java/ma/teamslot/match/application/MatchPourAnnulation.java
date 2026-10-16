package ma.teamslot.match.application;

import java.util.Set;
import java.util.UUID;

/** Ce qu'il faut savoir d'un match pour l'annuler (SCRUM-76). */
public record MatchPourAnnulation(UUID organisateur, String statut) {

    /** Annulable seulement avant d'être joué : en attente de paiement ou confirmé. */
    static final Set<String> STATUTS_ANNULABLES = Set.of("PENDING_PAYMENT", "CONFIRMED");

    public boolean annulable() {
        return STATUTS_ANNULABLES.contains(statut);
    }
}
