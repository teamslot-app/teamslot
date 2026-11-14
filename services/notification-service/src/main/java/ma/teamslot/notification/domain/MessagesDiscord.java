package ma.teamslot.notification.domain;

import java.util.UUID;

/**
 * Textes publiés dans le salon Discord de l'équipe (SCRUM-81). Le salon est partagé : aucun nom, surnom,
 * téléphone, e-mail ni identifiant de compte, seulement le début de l'identifiant du match.
 */
public final class MessagesDiscord {

    private MessagesDiscord() {
    }

    public static String participantAjoute(UUID matchId) {
        return "🔔 Match " + court(matchId) + " : un joueur a été ajouté au match.";
    }

    public static String demandeAcceptee(UUID matchId) {
        return "🔔 Match " + court(matchId) + " : une demande de place a été acceptée.";
    }

    public static String matchConfirme(UUID matchId, int joueursPrevenus) {
        return "✅ Match " + court(matchId) + " confirmé : " + joueursPrevenus
                + (joueursPrevenus > 1 ? " joueurs prévenus." : " joueur prévenu.");
    }

    /** Les 8 premiers caractères de l'identifiant : assez pour retrouver le match, rien de personnel. */
    static String court(UUID matchId) {
        return matchId.toString().substring(0, 8);
    }
}
