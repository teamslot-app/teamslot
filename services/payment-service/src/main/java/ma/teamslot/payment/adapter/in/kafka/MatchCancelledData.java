package ma.teamslot.payment.adapter.in.kafka;

import java.util.Objects;
import java.util.UUID;

/**
 * Champ utile de match.cancelled (docs/events.md) : seulement matchId. reason et cancelledBy sont ignorés,
 * et aucun montant n'est lu dans l'événement. matchId manquant = message invalide (direct au .dlt).
 */
public record MatchCancelledData(UUID matchId) {

    public MatchCancelledData {
        Objects.requireNonNull(matchId, "matchId");
    }
}
