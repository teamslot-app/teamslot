package ma.teamslot.notification.application;

/** Port d'envoi vers le salon Discord (adaptateur : DiscordWebhook). */
public interface EnvoiDiscord {

    /** false si aucune URL de webhook n'est configurée (local, tests) : rien n'est envoyé. */
    boolean actif();

    /** @return true si Discord a accepté le message. Ne lève jamais d'exception. */
    boolean envoyer(String contenu);
}
