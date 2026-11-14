package ma.teamslot.notification.adapter.in.kafka;

import ma.teamslot.events.Correlation;
import ma.teamslot.events.IdempotentProcessor;
import ma.teamslot.notification.application.VerificationEmailService;
import ma.teamslot.notification.domain.EmailDeVerification;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * SCRUM-134 : consomme identity.email_verification_requested, une seule fois par événement (IdempotentProcessor) :
 * un événement reçu deux fois n'envoie qu'un seul e-mail. Données invalides : .dlt sans nouvel essai ;
 * relais SMTP en panne : nouveaux essais espacés (ErreursKafka), puis .dlt.
 */
@Component
@ConditionalOnProperty(name = "teamslot.notification.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class IdentityEventsListener {

    static final String VERIFICATION_DEMANDEE = "identity.email_verification_requested";

    private final LecteurDEvenements lecteur;
    private final IdempotentProcessor processeur;
    private final VerificationEmailService service;

    IdentityEventsListener(LecteurDEvenements lecteur, IdempotentProcessor processeur,
                           VerificationEmailService service) {
        this.lecteur = lecteur;
        this.processeur = processeur;
        this.service = service;
    }

    @KafkaListener(topics = VERIFICATION_DEMANDEE)
    public void verificationDemandee(String message) {
        lecteur.lire(message, VERIFICATION_DEMANDEE, EmailVerificationRequestedData.class).ifPresent(recu -> {
            EmailDeVerification email = recu.data().versEmail();
            Correlation.pendant(recu.correlationId(), () -> processeur.traiterUneFois(recu.eventId(),
                    () -> service.surVerificationDemandee(email)));
        });
    }
}
