package ma.teamslot.events;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * Vrai MongoDB pour les tests du stockage MongoDB (SCRUM-111), en replica set d'un nœud comme en staging (SCRUM-115).
 * Depuis Testcontainers 2, le replica set n'est plus activé par défaut : withReplicaSet() est obligatoire,
 * sinon MongoDB refuse les transactions ("Transaction numbers are only allowed on a replica set member").
 * A utiliser avec les propriétés teamslot.events.store=mongodb et spring.flyway.enabled=false.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MongoDeTest {

    @Bean
    @ServiceConnection
    MongoDBContainer conteneurMongo() {
        return new MongoDBContainer("mongo:7.0").withReplicaSet();
    }
}
