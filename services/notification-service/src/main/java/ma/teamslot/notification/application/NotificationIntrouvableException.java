package ma.teamslot.notification.application;

/** Notification inexistante ou appartenant à un autre utilisateur : même réponse (404) dans les deux cas. */
public class NotificationIntrouvableException extends RuntimeException {

    public NotificationIntrouvableException() {
        super("Notification introuvable");
    }
}
