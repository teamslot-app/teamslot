package ma.teamslot.payment.adapter.in.kafka;

import ma.teamslot.events.IdempotentProcessor;
import ma.teamslot.payment.application.VenueOwnerRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * SCRUM-75 : lit venue.created et retient le gérant de chaque terrain, une seule fois par événement.
 * Désactivable avec teamslot.payment.consumers.enabled=false (tests sans Kafka).
 */
@Component
@ConditionalOnProperty(name = "teamslot.payment.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class VenueCreatedListener {

    static final String TOPIC = "venue.created";

    private final LecteurDEvenements lecteur;
    private final IdempotentProcessor processeur;
    private final VenueOwnerRepository gerants;

    VenueCreatedListener(LecteurDEvenements lecteur, IdempotentProcessor processeur, VenueOwnerRepository gerants) {
        this.lecteur = lecteur;
        this.processeur = processeur;
        this.gerants = gerants;
    }

    @KafkaListener(topics = TOPIC)
    public void recevoir(String message) {
        lecteur.lire(message, TOPIC, VenueCreatedData.class).ifPresent(recu ->
                processeur.traiterUneFois(recu.eventId(),
                        () -> gerants.enregistrerSiAbsent(recu.data().venueId(), recu.data().ownerId())));
    }
}
