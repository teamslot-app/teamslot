package ma.teamslot.notification.application;

import java.util.List;
import java.util.UUID;
import ma.teamslot.notification.domain.TypeNotification;

/** Port de persistance des notifications (adaptateur : JdbcNotificationStore). */
public interface NotificationStore {

    /** Ajoute un joueur avec compte à la projection du match ; sans effet s'il y est déjà. */
    void ajouterParticipant(UUID matchId, UUID userId);

    /** Joueurs avec compte connus pour ce match. */
    List<UUID> participants(UUID matchId);

    void enregistrerNotification(UUID userId, TypeNotification type, UUID matchId);

    /** Message à publier plus tard dans Discord (envoi par EnvoiDiscordEnAttente). */
    void enregistrerMessageDiscord(String contenu);

    /** Messages dont l'heure d'essai est passée, verrouillés pour cet envoi (SKIP LOCKED). */
    List<MessageAEnvoyer> messagesAEnvoyer(int limite);

    void marquerEnvoye(UUID messageId);

    /** Échec : nouvel essai plus tard (délai croissant), ou abandon après le dernier essai. */
    void noterEchec(UUID messageId, int essaisDejaFaits);

    record MessageAEnvoyer(UUID id, String contenu, int essais) {
    }
}
