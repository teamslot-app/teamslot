package ma.teamslot.match;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Accès direct à MongoDB pour préparer et vérifier les tests (SCRUM-112), à la place des requêtes SQL.
 * Les assertions sur l'API HTTP ne changent pas : seules la préparation et la lecture de la base changent.
 */
final class DonneesMongo {

    static final String MATCHES = "matches";
    static final String OUTBOX = "outbox_event";
    static final String TRAITES = "processed_event";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MongoTemplate mongo;

    DonneesMongo(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Vide les documents sans supprimer les collections (le validateur et les index restent). */
    void vider() {
        for (String collection : List.of(MATCHES, OUTBOX, TRAITES)) {
            mongo.remove(new Query(), collection);
        }
    }

    void viderEvenements() {
        mongo.remove(new Query(), OUTBOX);
        mongo.remove(new Query(), TRAITES);
    }

    /** Match FRIENDLY de 300 MAD le 20/10/2026 à 18h, sans participant (mêmes valeurs que les anciens INSERT). */
    UUID creerMatch(UUID organisateur, String statut) {
        UUID id = UUID.randomUUID();
        mongo.insert(new Document("_id", id.toString())
                .append("kind", "FRIENDLY")
                .append("status", statut)
                .append("visibility", "PRIVATE")
                .append("openSpots", 0)
                .append("ownerId", organisateur.toString())
                .append("payerId", organisateur.toString())
                .append("venueId", UUID.randomUUID().toString())
                .append("slotId", UUID.randomUUID().toString())
                .append("startsAt", Date.from(java.time.Instant.parse("2026-10-20T18:00:00Z")))
                .append("totalAmountCents", 30000L)
                .append("currency", "MAD")
                .append("acceptsOnSitePayment", false)
                .append("version", 0L)
                .append("createdAt", new Date())
                .append("participants", new ArrayList<Document>()), MATCHES);
        return id;
    }

    void ajouterParticipant(UUID matchId, UUID compte, String surnom, String statut, UUID ajoutePar) {
        Document participant = new Document("id", UUID.randomUUID().toString())
                .append("userId", compte == null ? null : compte.toString())
                .append("guestName", surnom)
                .append("guestKey", surnom == null ? null : surnom.replace(" ", "").toLowerCase(Locale.ROOT))
                .append("status", statut)
                .append("addedBy", ajoutePar.toString())
                .append("createdAt", new Date())
                .append("side", null);
        mongo.updateFirst(parId(matchId), new Update().push("participants", participant), MATCHES);
    }

    void changerStatut(UUID matchId, String statut) {
        mongo.updateFirst(parId(matchId), new Update().set("status", statut), MATCHES);
    }

    Document match(UUID matchId) {
        return mongo.findOne(parId(matchId), Document.class, MATCHES);
    }

    List<Document> matchs() {
        return mongo.findAll(Document.class, MATCHES);
    }

    long nombreMatchs() {
        return mongo.count(new Query(), MATCHES);
    }

    /** Participants de tous les matchs, dans l'ordre d'ajout. */
    List<Document> participants() {
        return matchs().stream().flatMap(m -> m.getList("participants", Document.class).stream()).toList();
    }

    long nombreParticipants() {
        return participants().size();
    }

    long nombreParticipants(Predicate<Document> condition) {
        return participants().stream().filter(condition).count();
    }

    List<Document> evenements() {
        return mongo.findAll(Document.class, OUTBOX);
    }

    long nombreEvenements() {
        return mongo.count(new Query(), OUTBOX);
    }

    List<Document> evenementsDe(UUID aggregateId) {
        return mongo.find(Query.query(Criteria.where("aggregateId").is(aggregateId.toString())), Document.class, OUTBOX);
    }

    /** Enveloppe JSON de l'événement (champ payload, texte JSON écrit par l'outbox de libs/events). */
    static JsonNode payload(Document evenement) {
        return JSON.readTree(evenement.getString("payload"));
    }

    private static Query parId(UUID matchId) {
        return Query.query(Criteria.where("_id").is(matchId.toString()));
    }
}
