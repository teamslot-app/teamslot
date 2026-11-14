package ma.teamslot.notification.adapter.in.kafka;

import java.time.Instant;
import java.util.UUID;
import ma.teamslot.notification.domain.EmailDeVerification;

/**
 * Champs de identity.email_verification_requested (docs/events.md) : userId, email, displayName, code, expiresAt.
 * Le code circule en clair dans cet événement seulement : toString() ne l'affiche jamais, ni l'adresse.
 */
public record EmailVerificationRequestedData(UUID userId, String email, String displayName, String code,
                                             Instant expiresAt) {

    /** Valide les champs (message invalide = exception, puis .dlt). */
    EmailDeVerification versEmail() {
        return new EmailDeVerification(userId, email, displayName, code, expiresAt);
    }

    @Override
    public String toString() {
        return "EmailVerificationRequestedData[userId=" + userId + ", expiresAt=" + expiresAt + "]";
    }
}
