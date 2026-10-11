package ma.teamslot.match.adapter.in.kafka;

import java.util.Set;
import ma.teamslot.match.application.ConfirmMatchService;
import ma.teamslot.match.events.EventEnvelope;
import ma.teamslot.match.events.EventReader;
import ma.teamslot.match.events.IdempotentProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * SCRUM-62 : lit payment.completed et payment.on_site_accepted, une seule fois par evenement.
 * Desactivable avec teamslot.match.consumers.enabled=false (tests sans Kafka).
 */
@Component
@ConditionalOnProperty(name = "teamslot.match.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentEventsListener {

    static final String PAYMENT_COMPLETED = "payment.completed";
    static final String PAYMENT_ON_SITE_ACCEPTED = "payment.on_site_accepted";
    private static final Set<String> TYPES = Set.of(PAYMENT_COMPLETED, PAYMENT_ON_SITE_ACCEPTED);
    private static final int VERSION_LUE = 1;
    private static final Logger LOG = LoggerFactory.getLogger(PaymentEventsListener.class);

    private final EventReader lecteur;
    private final IdempotentProcessor processeur;
    private final JsonMapper json;
    private final ConfirmMatchService service;

    public PaymentEventsListener(EventReader lecteur, IdempotentProcessor processeur, JsonMapper json,
                                 ConfirmMatchService service) {
        this.lecteur = lecteur;
        this.processeur = processeur;
        this.json = json;
        this.service = service;
    }

    @KafkaListener(topics = {PAYMENT_COMPLETED, PAYMENT_ON_SITE_ACCEPTED})
    public void recevoir(String message) {
        EventEnvelope<JsonNode> evenement = lecteur.lire(message);
        if (!TYPES.contains(evenement.type()) || evenement.version() != VERSION_LUE) {
            LOG.info("Evenement ignore {} ({} v{})", evenement.eventId(), evenement.type(), evenement.version());
            return;
        }
        PaymentEventData data = json.treeToValue(evenement.data(), PaymentEventData.class);
        processeur.traiterUneFois(evenement.eventId(), () -> service.surPaiement(data.matchId(), evenement.type()));
    }
}
