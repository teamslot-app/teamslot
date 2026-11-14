package ma.teamslot.notification.adapter.out.discord;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * Validation de l'URL du webhook (secret, SealedSecret notification-discord). Une URL qui ne vise pas l'API
 * des webhooks Discord en HTTPS est refusée au démarrage : le service ne doit jamais envoyer de requête vers
 * une autre adresse (erreur de configuration, ou abus de type SSRF).
 * autoriserLocal (tests seulement, application-test.properties) accepte http://localhost pour un faux Discord.
 */
final class UrlWebhook {

    private static final Pattern DISCORD =
            Pattern.compile("^https://(discord\\.com|discordapp\\.com)/api/webhooks/\\d+/[A-Za-z0-9_-]+$");
    private static final Pattern LOCAL = Pattern.compile("^http://(localhost|127\\.0\\.0\\.1):\\d+/api/webhooks/.+$");

    private UrlWebhook() {
    }

    /** @return l'URL validée, ou null si aucune URL n'est configurée (envoi désactivé). */
    static URI valider(String valeur, boolean autoriserLocal) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        String url = valeur.strip();
        if (DISCORD.matcher(url).matches() || (autoriserLocal && LOCAL.matcher(url).matches())) {
            return URI.create(url);
        }
        // Jamais l'URL dans le message : elle contient le jeton du webhook.
        throw new IllegalStateException("teamslot.notification.discord.webhook-url n'est pas une URL de webhook Discord valide");
    }
}
