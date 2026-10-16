package ma.teamslot.payment;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;

/** Vrai Kafka pour les tests des consommateurs (un seul conteneur, contexte Spring partagé entre les classes). */
@TestConfiguration(proxyBeanMethods = false)
public class KafkaDeTest {

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka:4.1.0");
    }
}
