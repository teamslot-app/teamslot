package ma.teamslot.notification.application;

import java.time.Clock;
import ma.teamslot.notification.domain.EmailDeVerification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * SCRUM-134 : envoi de l'e-mail de vérification. Appelé par le consommateur Kafka à l'intérieur de
 * IdempotentProcessor : un événement reçu deux fois n'envoie qu'un seul e-mail.
 * Jamais de code ni d'adresse dans les logs : seulement l'identifiant du compte.
 */
@Service
public class VerificationEmailService {

    private static final Logger LOG = LoggerFactory.getLogger(VerificationEmailService.class);

    private final EnvoiEmail envoi;
    private final Clock horloge;

    @Autowired
    public VerificationEmailService(EnvoiEmail envoi) {
        this(envoi, Clock.systemUTC());
    }

    VerificationEmailService(EnvoiEmail envoi, Clock horloge) {
        this.envoi = envoi;
        this.horloge = horloge;
    }

    public void surVerificationDemandee(EmailDeVerification email) {
        if (!envoi.actif()) {
            LOG.info("Envoi d'e-mail désactivé (aucun relais SMTP) : code non envoyé au compte {}", email.userId());
            return;
        }
        // Un code expiré ne sert à rien (événement rejoué depuis le .dlt, retard) : on ne l'envoie pas.
        if (email.expireAvant(horloge.instant())) {
            LOG.info("Code de vérification déjà expiré : pas d'e-mail pour le compte {}", email.userId());
            return;
        }
        envoi.envoyer(email);
        LOG.info("E-mail de vérification envoyé au compte {}", email.userId());
    }
}
