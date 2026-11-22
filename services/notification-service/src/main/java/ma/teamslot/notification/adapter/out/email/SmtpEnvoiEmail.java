package ma.teamslot.notification.adapter.out.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import ma.teamslot.notification.application.EnvoiEmail;
import ma.teamslot.notification.application.EnvoiEmailImpossibleException;
import ma.teamslot.notification.domain.EmailDeVerification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * SCRUM-134 : envoi par SMTP (Mailpit en local et en CI, relais Brevo en staging), texte + HTML.
 * Désactivé si spring.mail.host est vide. Le mot de passe SMTP est un secret (variable SMTP_PASSWORD) ;
 * ni lui, ni le code, ni l'adresse ne sont jamais écrits dans un log.
 */
@Component
class SmtpEnvoiEmail implements EnvoiEmail {

    private static final Logger LOG = LoggerFactory.getLogger(SmtpEnvoiEmail.class);

    private final ObjectProvider<JavaMailSender> expediteurs;
    private final boolean actif;
    private final String expediteur;

    SmtpEnvoiEmail(ObjectProvider<JavaMailSender> expediteurs,
                   @Value("${spring.mail.host:}") String hote,
                   @Value("${teamslot.notification.email.expediteur}") String expediteur) {
        this.expediteurs = expediteurs;
        this.actif = StringUtils.hasText(hote);
        this.expediteur = expediteur;
        if (actif) {
            LOG.info("Envoi d'e-mail activé");
        } else {
            LOG.info("Envoi d'e-mail désactivé : aucun relais SMTP configuré");
        }
    }

    @Override
    public boolean actif() {
        return actif;
    }

    @Override
    public void envoyer(EmailDeVerification email) {
        JavaMailSender smtp = expediteurs.getIfAvailable();
        if (smtp == null) {
            throw new EnvoiEmailImpossibleException("aucun JavaMailSender");
        }
        try {
            MimeMessage message = smtp.createMimeMessage();
            MimeMessageHelper aide = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            aide.setFrom(expediteur);
            aide.setTo(email.destinataire());
            aide.setSubject(EmailDeVerification.SUJET);
            Instant maintenant = Instant.now();
            aide.setText(email.texte(maintenant), email.html(maintenant));
            smtp.send(message);
        } catch (MailException | MessagingException e) {
            // Seulement le type de l'erreur : son message peut contenir l'adresse du destinataire
            throw new EnvoiEmailImpossibleException(e.getClass().getSimpleName());
        }
    }
}
