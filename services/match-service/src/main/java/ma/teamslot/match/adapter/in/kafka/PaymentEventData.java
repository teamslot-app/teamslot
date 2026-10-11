package ma.teamslot.match.adapter.in.kafka;

import java.util.Objects;
import java.util.UUID;

/** Champ utile de payment.completed et payment.on_site_accepted (docs/events.md) ; le reste est ignore. */
public record PaymentEventData(UUID matchId) {

    public PaymentEventData {
        Objects.requireNonNull(matchId, "matchId");
    }
}
