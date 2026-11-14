package ma.teamslot.notification.application;

/**
 * Le relais SMTP a refusé l'e-mail ou n'a pas répondu. Volontairement SANS la cause d'origine : les messages
 * d'erreur de JavaMail peuvent contenir l'adresse du destinataire, et cette exception finit dans les logs.
 */
public class EnvoiEmailImpossibleException extends RuntimeException {

    public EnvoiEmailImpossibleException(String typeErreur) {
        super("Envoi de l'e-mail impossible (" + typeErreur + ")");
    }
}
