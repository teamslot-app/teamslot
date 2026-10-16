package ma.teamslot.match;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * Vrai MongoDB pour les tests de match-service (SCRUM-112), en replica set d'un nœud comme en staging (SCRUM-115).
 * withReplicaSet() est obligatoire depuis Testcontainers 2 : sans replica set, MongoDB refuse les transactions
 * des services (match + outbox dans la même transaction).
 */
@TestConfiguration(proxyBeanMethods = false)
public class MongoDeTest {

    @Bean
    @ServiceConnection
    MongoDBContainer conteneurMongo() {
        return new MongoDBContainer("mongo:7.0").withReplicaSet();
    }
}
