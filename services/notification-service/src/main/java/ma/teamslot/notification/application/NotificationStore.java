package ma.teamslot.notification.application;

import java.util.List;
import java.util.UUID;
import ma.teamslot.notification.domain.Notification;
import ma.teamslot.notification.domain.Repere;
import ma.teamslot.notification.domain.TypeNotification;

/** Port de persistance des notifications (adaptateur : JdbcNotificationStore). */
public interface NotificationStore {

    /** Ajoute un joueur avec compte à la projection du match ; sans effet s'il y est déjà. */
    void ajouterParticipant(UUID matchId, UUID userId);

    /** Joueurs avec compte connus pour ce match. */
    List<UUID> participants(UUID matchId);

    void enregistrerNotification(UUID userId, TypeNotification type, UUID matchId);

    /**
     * Notifications de cet utilisateur, les plus récentes d'abord.
     *
     * @param apres null pour la première tranche, sinon la dernière notification déjà renvoyée
     * @param nombre nombre maximum de notifications renvoyées
     */
    List<Notification> notificationsDe(UUID userId, Repere apres, int nombre);

    /**
     * Marque la notification comme lue si elle appartient à cet utilisateur (une notification déjà lue garde
     * sa première date).
     *
     * @return false si elle n'existe pas ou appartient à quelqu'un d'autre
     */
    boolean marquerLue(UUID notificationId, UUID userId);
}
