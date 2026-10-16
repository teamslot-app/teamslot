package ma.teamslot.events.internal;

import ma.teamslot.events.EventReader;
import ma.teamslot.events.IdempotentProcessor;
import ma.teamslot.events.Outbox;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Auto-configuration de libs/events (ADR-015, SCRUM-88) : un service obtient l'outbox, le relais,
 * la lecture des messages, l'idempotence et le .dlt en ajoutant seulement la dépendance.
 * Stockage (SCRUM-111) : PostgreSQL ou MongoDB, choisi par ChoixDuStockage (une seule implémentation active).
 * Chaque bean porte @ConditionalOnMissingBean : un service peut le remplacer par le sien.
 * Déclarée dans META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports.
 *
 * Les beans liés à une base sont dans des classes imbriquées : leurs conditions sont évaluées avant que
 * leurs méthodes soient lues, donc un service MongoDB démarre sans JDBC (et inversement).
 */
@AutoConfiguration
public class EventsAutoConfiguration {

    /** Suffixe du topic des messages en échec (catalogue docs/events.md). Spring Kafka utilise "-dlt" par défaut. */
    static final String SUFFIXE_DLT = ".dlt";
    private static final long PAUSE_ENTRE_ESSAIS_MS = 1000L;
    private static final long NOUVEAUX_ESSAIS = 2L;
    private static final String NOM_DU_SERVICE = "spring.application.name";

    @Bean
    @ConditionalOnMissingBean
    EventReader eventReader(JsonMapper jsonMapper) {
        return new EventReader(jsonMapper);
    }

    /**
     * Erreurs des consommateurs Kafka (docs/events.md, "Message invalide") :
     * - erreur passagère : 2 nouveaux essais, à 1 seconde d'intervalle ;
     * - message invalide (JSON cassé, enveloppe incomplète) : aucun nouvel essai ;
     * - puis le message part dans "<topic>.dlt" et les suivants sont traités.
     * Spring Boot branche ce bean sur les @KafkaListener du service.
     */
    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    DefaultErrorHandler gestionnaireErreursKafka(KafkaTemplate<?, ?> kafka) {
        // Partition -1 : Kafka la choisit avec la clé, même si le .dlt a moins de partitions que le topic.
        DeadLetterPublishingRecoverer versDlt = new DeadLetterPublishingRecoverer(kafka,
                (message, erreur) -> new TopicPartition(message.topic() + SUFFIXE_DLT, -1));

        DefaultErrorHandler gestionnaire =
                new DefaultErrorHandler(versDlt, new FixedBackOff(PAUSE_ENTRE_ESSAIS_MS, NOUVEAUX_ESSAIS));
        // Un message illisible échouerait à chaque essai : directement dans le .dlt.
        gestionnaire.addNotRetryableExceptions(JacksonException.class);
        return gestionnaire;
    }

    /** Services PostgreSQL : tables outbox_event et processed_event (V2__outbox.sql). */
    @Configuration(proxyBeanMethods = false)
    @Conditional(ChoixDuStockage.Jdbc.class)
    static class StockageJdbc {

        /** Le nom du service devient le champ producer des enveloppes : il est obligatoire. */
        @Bean
        @ConditionalOnMissingBean(Outbox.class)
        Outbox outbox(JdbcClient jdbc, JsonMapper jsonMapper, Environment environnement) {
            return new JdbcOutbox(jdbc, jsonMapper, environnement.getRequiredProperty(NOM_DU_SERVICE));
        }

        @Bean
        @ConditionalOnMissingBean
        OutboxRelay outboxRelay(JdbcClient jdbc, KafkaTemplate<String, String> kafka) {
            return new OutboxRelay(jdbc, kafka);
        }

        @Bean
        @ConditionalOnMissingBean(IdempotentProcessor.class)
        IdempotentProcessor idempotentProcessor(JdbcClient jdbc) {
            return new JdbcIdempotentProcessor(jdbc);
        }
    }

    /** Services MongoDB (ADR-017) : collections outbox_event et processed_event. */
    @Configuration(proxyBeanMethods = false)
    @Conditional(ChoixDuStockage.MongoDb.class)
    static class StockageMongoDb {

        /**
         * Spring Boot ne crée pas de gestionnaire de transactions MongoDB : sans lui, @Transactional
         * ne fait rien avec MongoDB. Les transactions demandent un replica set (SCRUM-115).
         */
        @Bean
        @ConditionalOnMissingBean(MongoTransactionManager.class)
        MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory fabrique) {
            return new MongoTransactionManager(fabrique);
        }

        @Bean
        @ConditionalOnMissingBean(Outbox.class)
        Outbox outbox(MongoTemplate mongo, MongoDatabaseFactory fabrique, JsonMapper jsonMapper,
                Environment environnement) {
            return new MongoOutbox(mongo, fabrique, jsonMapper, environnement.getRequiredProperty(NOM_DU_SERVICE));
        }

        @Bean
        @ConditionalOnMissingBean
        MongoOutboxRelay mongoOutboxRelay(MongoTemplate mongo, KafkaTemplate<String, String> kafka) {
            return new MongoOutboxRelay(mongo, kafka);
        }

        @Bean
        @ConditionalOnMissingBean(IdempotentProcessor.class)
        IdempotentProcessor idempotentProcessor(MongoTemplate mongo, MongoTransactionManager transactions) {
            return new MongoIdempotentProcessor(mongo, transactions);
        }
    }

    /** Active le relais planifié. Désactivable avec teamslot.outbox.relay.enabled=false (tests). */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = "teamslot.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
    static class PlanificationDuRelais {
    }
}
