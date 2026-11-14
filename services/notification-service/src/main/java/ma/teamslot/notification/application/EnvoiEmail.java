package ma.teamslot.notification.application;

import ma.teamslot.notification.domain.EmailDeVerification;

/** Port d'envoi d'e-mail (adaptateur : SmtpEnvoiEmail). */
public interface EnvoiEmail {

    /** Faux si aucun relais SMTP n'est configuré (envoi désactivé). */
    boolean actif();

    /** Lève EnvoiEmailImpossibleException si le relais refuse ou ne répond pas (nouvel essai, puis .dlt). */
    void envoyer(EmailDeVerification email);
}
