package ma.teamslot.notification.application;

import java.util.List;
import java.util.UUID;
import ma.teamslot.notification.domain.MessagesDiscord;
import ma.teamslot.notification.domain.TypeNotification;
import org.springframework.stereotype.Service;

/**
 * Cas d'usage de SCRUM-81. Appelé par les consommateurs Kafka à l'intérieur de IdempotentProcessor :
 * tout est écrit dans la même transaction que processed_event (un événement rejoué n'a aucun effet).
 * L'envoi vers Discord se fait plus tard, hors de cette transaction (EnvoiDiscordEnAttente).
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
        store.enregistrerMessageDiscord(MessagesDiscord.participantAjoute(matchId));
    }

    /** match.join_accepted : le joueur accepté devient participant (cet événement ne publie pas participant_added). */
    public void surDemandeAcceptee(UUID matchId, UUID userId) {
        store.ajouterParticipant(matchId, userId);
        store.enregistrerNotification(userId, TypeNotification.MATCH_JOIN_ACCEPTED, matchId);
        store.enregistrerMessageDiscord(MessagesDiscord.demandeAcceptee(matchId));
    }

    /** match.confirmed : une notification pour chaque joueur avec compte connu du match. */
    public void surMatchConfirme(UUID matchId) {
        List<UUID> joueurs = store.participants(matchId);
        joueurs.forEach(userId -> store.enregistrerNotification(userId, TypeNotification.MATCH_CONFIRMED, matchId));
        store.enregistrerMessageDiscord(MessagesDiscord.matchConfirme(matchId, joueurs.size()));
    }
}
