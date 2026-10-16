package ma.teamslot.events.internal;

import java.util.Locale;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;

/**
 * Choisit le stockage de l'outbox et de l'idempotence (SCRUM-111) :
 * - propriété teamslot.events.store = jdbc ou mongodb, si elle est donnée ;
 * - sinon automatiquement : jdbc si JDBC est présent (services PostgreSQL), mongodb sinon (match-service, ADR-017).
 * Une seule implémentation est donc active : jamais deux beans Outbox.
 */
final class ChoixDuStockage {

    static final String PROPRIETE = "teamslot.events.store";
    static final String JDBC = "jdbc";
    static final String MONGODB = "mongodb";

    private static final String CLASSE_JDBC = "org.springframework.jdbc.core.simple.JdbcClient";
    private static final String CLASSE_MONGODB = "org.springframework.data.mongodb.core.MongoTemplate";

    private ChoixDuStockage() {
    }

    static String choisir(ConditionContext contexte) {
        String demande = contexte.getEnvironment().getProperty(PROPRIETE);
        if (demande != null && !demande.isBlank()) {
            return demande.trim().toLowerCase(Locale.ROOT);
        }
        ClassLoader chargeur = contexte.getClassLoader();
        if (ClassUtils.isPresent(CLASSE_JDBC, chargeur)) {
            return JDBC;
        }
        if (ClassUtils.isPresent(CLASSE_MONGODB, chargeur)) {
            return MONGODB;
        }
        return "aucun";
    }

    /** Stockage PostgreSQL : JDBC choisi et présent. */
    static final class Jdbc implements Condition {
        @Override
        public boolean matches(ConditionContext contexte, AnnotatedTypeMetadata metadata) {
            return JDBC.equals(choisir(contexte)) && ClassUtils.isPresent(CLASSE_JDBC, contexte.getClassLoader());
        }
    }

    /** Stockage MongoDB : MongoDB choisi et Spring Data MongoDB présent. */
    static final class MongoDb implements Condition {
        @Override
        public boolean matches(ConditionContext contexte, AnnotatedTypeMetadata metadata) {
            return MONGODB.equals(choisir(contexte)) && ClassUtils.isPresent(CLASSE_MONGODB, contexte.getClassLoader());
        }
    }
}
