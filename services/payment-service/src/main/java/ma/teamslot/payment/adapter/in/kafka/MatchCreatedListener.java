package ma.teamslot.payment.adapter.in.kafka;

import ma.teamslot.events.EventEnvelope;
import ma.teamslot.events.EventReader;
import ma.teamslot.events.IdempotentProcessor;
import ma.teamslot.payment.application.CreatePendingPaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lit match.created et cree le paiement en attente, une seule fois par evenement (S6).
 * Desactivable avec teamslot.payment.consumers.enabled=false (tests sans Kafka).
 */
@Component
@ConditionalOnProperty(name = "teamslot.payment.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class MatchCreatedListener {

    static final String TOPIC = "match.created";
    private static final int VERSION_LUE = 1;
    private static final Logger LOG = LoggerFactory.getLogger(MatchCreatedListener.class);

    private final EventReader lecteur;
    private final IdempotentProcessor processeur;
    private final JsonMapper json;
    private final CreatePendingPaymentService service;

    public MatchCreatedListener(EventReader lecteur, IdempotentProcessor processeur, JsonMapper json,
                                CreatePendingPaymentService service) {
        this.lecteur = lecteur;
        this.processeur = processeur;
        this.json = json;
        this.service = service;
    }

    @KafkaListener(topics = TOPIC)
    public void recevoir(String message) {
        EventEnvelope<JsonNode> evenement = lecteur.lire(message);
        if (!TOPIC.equals(evenement.type()) || evenement.version() != VERSION_LUE) {
            // Une autre version est publiee en parallele pendant une migration : on ne lit que la v1.
            LOG.info("Evenement ignore {} ({} v{})", evenement.eventId(), evenement.type(), evenement.version());
            return;
        }
        MatchCreatedData data = json.treeToValue(evenement.data(), MatchCreatedData.class);
        processeur.traiterUneFois(evenement.eventId(), () -> service.surMatchCree(data.matchId(),
                data.payerId(), data.totalAmount(), data.currency(), data.acceptsOnSitePayment()));
    }
}
