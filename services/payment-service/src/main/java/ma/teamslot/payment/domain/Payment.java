package ma.teamslot.payment.domain;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Paiement d'un match (SCRUM-62). Cree en PENDING a la reception de match.created ;
 * le montant et le droit au paiement sur place viennent de l'evenement, jamais du client.
 */
public record Payment(UUID id, UUID matchId, UUID payerId, long totalAmountCents, String currency,
                      boolean acceptsOnSitePayment, PaymentMode mode, PaymentStatus status) {

    private static final Pattern DEVISE = Pattern.compile("[A-Z]{3}");

    public Payment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(payerId, "payerId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(status, "status");
        if (totalAmountCents < 0) {
            throw new IllegalArgumentException("Le montant ne peut pas etre negatif.");
        }
        if (!DEVISE.matcher(currency).matches()) {
            throw new IllegalArgumentException("Devise invalide (3 lettres majuscules attendues).");
        }
    }

    /** Paiement en attente, cree a partir de match.created. */
    public static Payment enAttente(UUID matchId, UUID payerId, long totalAmountCents, String currency,
                                    boolean acceptsOnSitePayment) {
        return new Payment(UUID.randomUUID(), matchId, payerId, totalAmountCents, currency,
                acceptsOnSitePayment, null, PaymentStatus.PENDING);
    }
}
