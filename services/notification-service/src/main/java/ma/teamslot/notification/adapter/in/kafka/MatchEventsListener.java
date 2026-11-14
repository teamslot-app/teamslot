package ma.teamslot.notification.adapter.in.kafka;

import ma.teamslot.events.Correlation;
import ma.teamslot.events.IdempotentProcessor;
import ma.teamslot.notification.application.NotificationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * SCRUM-81 : consomme les événements de match, une seule fois par événement (IdempotentProcessor, S6) :
 * un événement reçu deux fois n'envoie qu'un seul message. Données invalides : exception, puis .dlt (libs/events).
 * Désactivable avec teamslot.notification.consumers.enabled=false (tests sans Kafka).
 */
@Component
@ConditionalOnProperty(name = "teamslot.notification.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class MatchEventsListener {

    static final String PARTICIPANT_AJOUTE = "match.participant_added";
    static final String DEMANDE_ACCEPTEE = "match.join_accepted";
    static final String MATCH_CONFIRME = "match.confirmed";

    private final LecteurDEvenements lecteur;
    private final IdempotentProcessor processeur;
    private final NotificationService service;

    MatchEventsListener(LecteurDEvenements lecteur, IdempotentProcessor processeur, NotificationService service) {
        this.lecteur = lecteur;
        this.processeur = processeur;
        this.service = service;
    }

    @KafkaListener(topics = PARTICIPANT_AJOUTE)
    public void participantAjoute(String message) {
        lecteur.lire(message, PARTICIPANT_AJOUTE, ParticipantAddedData.class).ifPresent(recu ->
                Correlation.pendant(recu.correlationId(), () -> processeur.traiterUneFois(recu.eventId(),
                        () -> service.surParticipantAjoute(recu.data().matchId(), recu.data().userId()))));
    }

    @KafkaListener(topics = DEMANDE_ACCEPTEE)
    public void demandeAcceptee(String message) {
        lecteur.lire(message, DEMANDE_ACCEPTEE, JoinAcceptedData.class).ifPresent(recu ->
                Correlation.pendant(recu.correlationId(), () -> processeur.traiterUneFois(recu.eventId(),
                        () -> service.surDemandeAcceptee(recu.data().matchId(), recu.data().userId()))));
    }

    @KafkaListener(topics = MATCH_CONFIRME)
    public void matchConfirme(String message) {
        lecteur.lire(message, MATCH_CONFIRME, MatchConfirmedData.class).ifPresent(recu ->
                Correlation.pendant(recu.correlationId(), () -> processeur.traiterUneFois(recu.eventId(),
                        () -> service.surMatchConfirme(recu.data().matchId()))));
    }
}
