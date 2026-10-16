package ma.teamslot.payment.adapter.in.kafka;

import ma.teamslot.events.IdempotentProcessor;
import ma.teamslot.payment.application.CreatePendingPaymentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Lit match.created et cree le paiement en attente, une seule fois par evenement (S6).
 * Desactivable avec teamslot.payment.consumers.enabled=false (tests sans Kafka).
 */
@Component
@ConditionalOnProperty(name = "teamslot.payment.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class MatchCreatedListener {

    static final String TOPIC = "match.created";

    private final LecteurDEvenements lecteur;
    private final IdempotentProcessor processeur;
    private final CreatePendingPaymentService service;

    MatchCreatedListener(LecteurDEvenements lecteur, IdempotentProcessor processeur,
                         CreatePendingPaymentService service) {
        this.lecteur = lecteur;
        this.processeur = processeur;
        this.service = service;
    }

    @KafkaListener(topics = TOPIC)
    public void recevoir(String message) {
        lecteur.lire(message, TOPIC, MatchCreatedData.class).ifPresent(recu -> {
            MatchCreatedData data = recu.data();
            processeur.traiterUneFois(recu.eventId(), () -> service.surMatchCree(data.matchId(),
                    data.payerId(), data.totalAmount(), data.currency(), data.acceptsOnSitePayment()));
        });
    }
}
