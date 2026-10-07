package ma.teamslot.servicetemplate.events;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

/**
 * Erreurs des consommateurs Kafka (docs/events.md, "Message invalide") :
 * - erreur passagère : 2 nouveaux essais, à 1 seconde d'intervalle ;
 * - message invalide (JSON cassé, enveloppe incomplète) : aucun nouvel essai ;
 * - puis le message part dans "<topic>.dlt" (catalogue) et les suivants sont traités.
 * Spring Kafka utilise "-dlt" par défaut : le nom est donc fixé ici.
 */
@Configuration
class KafkaErreursConfig {

    static final String SUFFIXE_DLT = ".dlt";
    private static final long PAUSE_ENTRE_ESSAIS_MS = 1000L;
    private static final long NOUVEAUX_ESSAIS = 2L;

    @Bean
    DefaultErrorHandler gestionnaireErreursKafka(KafkaTemplate<?, ?> kafka) {
        DeadLetterPublishingRecoverer versDlt = new DeadLetterPublishingRecoverer(kafka,
                (message, erreur) -> new TopicPartition(message.topic() + SUFFIXE_DLT, message.partition()));

        DefaultErrorHandler gestionnaire =
                new DefaultErrorHandler(versDlt, new FixedBackOff(PAUSE_ENTRE_ESSAIS_MS, NOUVEAUX_ESSAIS));
        // Un message illisible échouerait à chaque essai : directement dans le .dlt.
        gestionnaire.addNotRetryableExceptions(JacksonException.class);
        return gestionnaire;
    }
}
