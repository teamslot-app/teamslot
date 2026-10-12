package ma.teamslot.payment.adapter.in.kafka;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Champs de match.created utiles a payment (docs/events.md). Les autres champs sont ignores.
 * Message recu = entree non fiable : un champ manquant rend le message invalide (direct au .dlt).
 */
public record MatchCreatedData(UUID matchId, UUID payerId, Long totalAmount, String currency,
                               Boolean acceptsOnSitePayment) {

    private static final Pattern DEVISE = Pattern.compile("[A-Z]{3}");

    public MatchCreatedData {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(payerId, "payerId");
        Objects.requireNonNull(totalAmount, "totalAmount");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(acceptsOnSitePayment, "acceptsOnSitePayment");
        if (totalAmount < 0 || !DEVISE.matcher(currency).matches()) {
            throw new IllegalArgumentException("Montant negatif ou devise invalide dans match.created");
        }
    }
}
