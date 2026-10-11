package ma.teamslot.payment.application;

import java.util.UUID;

/** Aucun paiement pour ce match (match inconnu, ou match.created pas encore recu) : reponse 404. */
public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(UUID matchId) {
        super("Aucun paiement pour le match " + matchId);
    }
}
