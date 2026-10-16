package ma.teamslot.match.adapter.out.persistence;

import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.COLLECTION;

import java.util.Arrays;
import java.util.List;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.CollectionOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.validation.Validator;
import org.springframework.stereotype.Component;

/**
 * Crée la collection matches, son validateur JSON Schema et ses index au démarrage (SCRUM-112).
 * Pas de Flyway pour MongoDB : tout est idempotent, on peut redémarrer sans risque.
 *
 * Le validateur est le filet de sécurité de la base, comme les CHECK de l'ancien schéma PostgreSQL :
 * le domaine valide d'abord (Participant), la base refuse ensuite ce qui aurait échappé au code.
 */
@Component
class CollectionMatches implements InitializingBean {

    private static final Logger LOG = LoggerFactory.getLogger(CollectionMatches.class);

    private static final List<String> STATUTS_MATCH = List.of("PENDING_PAYMENT", "CONFIRMED", "CANCELLED", "PLAYED");
    private static final List<String> STATUTS_PARTICIPANT = List.of("INVITED", "CONFIRMED", "REQUESTED", "DECLINED");
    private static final List<String> TEXTE_OU_NULL = List.of("string", "null");

    private final MongoTemplate mongo;

    CollectionMatches(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void afterPropertiesSet() {
        creerLaCollection();
        // Mes matchs (organisateur) et, plus tard, les matchs où je suis invité.
        mongo.indexOps(COLLECTION).createIndex(new Index().on("ownerId", Sort.Direction.ASC).named("idx_matches_owner"));
        mongo.indexOps(COLLECTION).createIndex(new Index().on("participants.userId", Sort.Direction.ASC)
                .sparse().named("idx_matches_participant_compte"));
    }

    /**
     * Le validateur n'est posé qu'à la création : le modifier ensuite (collMod) demande le rôle dbAdmin,
     * que match_user n'a pas (SCRUM-115, readWrite seulement). Pour le changer : mongosh avec root.
     */
    private void creerLaCollection() {
        if (mongo.collectionExists(COLLECTION)) {
            return;
        }
        try {
            mongo.createCollection(COLLECTION, CollectionOptions.empty().validator(Validator.document(schema())));
            LOG.info("Collection {} créée avec son validateur", COLLECTION);
        } catch (DataAccessException e) {
            // Créée entre-temps par une autre instance : rien à faire.
            if (!mongo.collectionExists(COLLECTION)) {
                throw e;
            }
        }
    }

    static Document schema() {
        Document participant = new Document("bsonType", "object")
                .append("required", List.of("id", "userId", "guestName", "status", "addedBy", "createdAt", "side"))
                .append("properties", new Document()
                        .append("id", type("string"))
                        .append("userId", new Document("bsonType", TEXTE_OU_NULL))
                        .append("guestName", new Document("bsonType", TEXTE_OU_NULL)
                                .append("minLength", 1).append("maxLength", 40))
                        .append("guestKey", new Document("bsonType", TEXTE_OU_NULL))
                        .append("status", new Document("enum", STATUTS_PARTICIPANT))
                        .append("addedBy", type("string"))
                        .append("createdAt", type("date"))
                        .append("side", new Document("enum", Arrays.asList("A", "B", null))))
                // Soit un compte, soit un surnom, jamais les deux (ancienne contrainte participant_compte_ou_surnom).
                .append("oneOf", List.of(
                        new Document("properties", new Document("userId", type("string"))
                                .append("guestName", type("null"))),
                        new Document("properties", new Document("userId", type("null"))
                                .append("guestName", type("string")))));

        Document match = new Document("bsonType", "object")
                .append("required", List.of("_id", "kind", "status", "visibility", "openSpots", "ownerId", "payerId",
                        "venueId", "slotId", "startsAt", "totalAmountCents", "currency", "acceptsOnSitePayment",
                        "version", "createdAt", "participants"))
                .append("properties", new Document()
                        .append("_id", type("string"))
                        .append("kind", type("string"))
                        .append("status", new Document("enum", STATUTS_MATCH))
                        .append("visibility", type("string"))
                        .append("openSpots", new Document("bsonType", List.of("int", "long")).append("minimum", 0))
                        .append("ownerId", type("string"))
                        .append("payerId", type("string"))
                        .append("venueId", type("string"))
                        .append("slotId", type("string"))
                        .append("startsAt", type("date"))
                        .append("totalAmountCents", new Document("bsonType", List.of("int", "long")).append("minimum", 0))
                        .append("currency", type("string").append("minLength", 3).append("maxLength", 3))
                        .append("acceptsOnSitePayment", type("bool"))
                        .append("version", new Document("bsonType", List.of("int", "long")).append("minimum", 0))
                        .append("createdAt", type("date"))
                        .append("participants", new Document("bsonType", "array").append("items", participant)));

        return new Document("$jsonSchema", match);
    }

    private static Document type(String bsonType) {
        return new Document("bsonType", bsonType);
    }
}
