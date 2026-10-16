package ma.teamslot.payment.adapter.in.kafka;

import ma.teamslot.events.IdempotentProcessor;
import ma.teamslot.payment.application.RefundService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * SCRUM-76 : lit match.cancelled et rembourse le paiement en ligne, une seule fois par événement (S6).
 * Désactivable avec teamslot.payment.consumers.enabled=false (tests sans Kafka).
 */
@Component
@ConditionalOnProperty(name = "teamslot.payment.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class MatchCancelledListener {

    static final String TOPIC = "match.cancelled";

    private final LecteurDEvenements lecteur;
    private final IdempotentProcessor processeur;
    private final RefundService service;

    MatchCancelledListener(LecteurDEvenements lecteur, IdempotentProcessor processeur, RefundService service) {
        this.lecteur = lecteur;
        this.processeur = processeur;
        this.service = service;
    }

    @KafkaListener(topics = TOPIC)
    public void recevoir(String message) {
        lecteur.lire(message, TOPIC, MatchCancelledData.class).ifPresent(recu ->
                processeur.traiterUneFois(recu.eventId(), () -> service.surMatchAnnule(recu.data().matchId())));
    }
}
