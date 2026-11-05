package ma.teamslot.payment.adapter.in.kafka;

import java.util.Objects;
import java.util.UUID;

/**
 * Champs de venue.created utiles à payment (docs/events.md) : le terrain et son gérant. Les autres sont ignorés.
 * Message reçu = entrée non fiable : un champ manquant rend le message invalide (direct au .dlt).
 */
public record VenueCreatedData(UUID venueId, UUID ownerId) {

    public VenueCreatedData {
        Objects.requireNonNull(venueId, "venueId");
        Objects.requireNonNull(ownerId, "ownerId");
    }
}
