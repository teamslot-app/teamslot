package ma.teamslot.payment.application;

import java.util.Optional;
import java.util.UUID;
import ma.teamslot.payment.domain.Payment;

/** Port de sortie : stockage des paiements. */
public interface PaymentRepository {

    /** Enregistre le paiement s'il n'en existe pas deja un pour ce match (S6). @return true si cree. */
    boolean creerSiAbsent(Payment payment);

    /** Paiement du match, sans verrou (lecture). */
    Optional<Payment> trouver(UUID matchId);

    /** Paiement du match, verrouille jusqu'a la fin de la transaction (deux paiements simultanes : un seul passe). */
    Optional<Payment> trouverPourMiseAJour(UUID matchId);

    void mettreAJour(Payment payment);
}
