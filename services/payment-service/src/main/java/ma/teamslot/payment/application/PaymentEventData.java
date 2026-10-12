package ma.teamslot.payment.application;

import java.util.UUID;

/** Champs data de payment.completed (avec mode) et payment.on_site_accepted (sans mode), docs/events.md. */
public final class PaymentEventData {

    private PaymentEventData() {
    }

    public record Completed(UUID matchId, UUID paymentId, UUID payerId, String mode, long totalAmount,
                            String currency) {
    }

    public record OnSiteAccepted(UUID matchId, UUID paymentId, UUID payerId, long totalAmount,
                                 String currency) {
    }
}
