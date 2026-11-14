package ma.teamslot.notification.application;

import java.util.List;
import java.util.UUID;
import ma.teamslot.notification.domain.Notification;
import ma.teamslot.notification.domain.Repere;
import ma.teamslot.notification.domain.TypeNotification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cas d'usage de SCRUM-81 : le joueur est prévenu dans l'application (liste « Mes notifications »).
 * Les méthodes sur… sont appelées par les consommateurs Kafka à l'intérieur de IdempotentProcessor :
 * tout est écrit dans la même transaction que processed_event (un événement rejoué n'a aucun effet).
 */
@Service
public class NotificationService {

    private final NotificationStore store;

    public NotificationService(NotificationStore store) {
        this.store = store;
    }

    /** match.participant_added : rien pour un invité sans compte (aucune coordonnée, critère 3). */
    public void surParticipantAjoute(UUID matchId, UUID userId) {
        if (userId == null) {
            return;
        }
        store.ajouterParticipant(matchId, userId);
        store.enregistrerNotification(userId, TypeNotification.MATCH_PARTICIPANT_ADDED, matchId);
    }

    /** match.join_accepted : le joueur accepté devient participant (cet événement ne publie pas participant_added). */
    public void surDemandeAcceptee(UUID matchId, UUID userId) {
        store.ajouterParticipant(matchId, userId);
        store.enregistrerNotification(userId, TypeNotification.MATCH_JOIN_ACCEPTED, matchId);
    }

    /** match.confirmed : une notification pour chaque joueur avec compte connu du match. */
    public void surMatchConfirme(UUID matchId) {
        store.participants(matchId)
                .forEach(userId -> store.enregistrerNotification(userId, TypeNotification.MATCH_CONFIRMED, matchId));
    }

    /**
     * Une tranche de « Mes notifications ». On lit une notification de plus que demandé : si elle existe,
     * il y a une tranche suivante, qui commencera après la dernière notification renvoyée.
     */
    @Transactional(readOnly = true)
    public Tranche mesNotifications(UUID userId, Repere apres, int nombre) {
        List<Notification> lues = store.notificationsDe(userId, apres, nombre + 1);
        if (lues.size() <= nombre) {
            return new Tranche(lues, null);
        }
        List<Notification> items = List.copyOf(lues.subList(0, nombre));
        return new Tranche(items, Repere.de(items.getLast()));
    }

    /** Idempotent ; 404 si la notification n'est pas à cet utilisateur (on ne révèle pas qu'elle existe). */
    @Transactional
    public void marquerLue(UUID notificationId, UUID userId) {
        if (!store.marquerLue(notificationId, userId)) {
            throw new NotificationIntrouvableException();
        }
    }

    /** @param suivante null quand il n'y a plus de notifications */
    public record Tranche(List<Notification> items, Repere suivante) {
    }
}
