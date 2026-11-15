package ma.teamslot.notification.adapter.in.kafka;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import tools.jackson.core.JacksonException;

/**
 * SCRUM-134 : erreurs des consommateurs de notification-service. Remplace celui de libs/events (même règles :
 * message illisible ou invalide = .dlt tout de suite) avec des essais à DÉLAI CROISSANT pour une erreur
 * passagère comme un relais SMTP en panne : 2 s, 4 s, 8 s, 16 s, puis le message part dans « topic.dlt ».
 */
@Configuration(proxyBeanMethods = false)
class ErreursKafka {

    static final long PREMIER_DELAI_MS = 2_000L;
    static final int NOUVEAUX_ESSAIS = 4;

    @Bean
    CommonErrorHandler gestionnaireErreursNotification(KafkaTemplate<?, ?> kafka) {
        // Partition -1 : Kafka la choisit avec la clé, même si le .dlt a moins de partitions que le topic.
        DeadLetterPublishingRecoverer versDlt = new DeadLetterPublishingRecoverer(kafka,
                (message, erreur) -> new TopicPartition(message.topic() + ".dlt", -1));
        ExponentialBackOffWithMaxRetries delais = new ExponentialBackOffWithMaxRetries(NOUVEAUX_ESSAIS);
        delais.setInitialInterval(PREMIER_DELAI_MS);
        delais.setMultiplier(2.0);
        DefaultErrorHandler gestionnaire = new DefaultErrorHandler(versDlt, delais);
        gestionnaire.addNotRetryableExceptions(JacksonException.class, IllegalArgumentException.class,
                NullPointerException.class);
        return gestionnaire;
    }
}
