package ma.teamslot.events.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.teamslot.events.EventEnvelope;
import ma.teamslot.events.EventReader;
import ma.teamslot.events.MongoDeTest;
import ma.teamslot.events.Outbox;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/** Outbox MongoDB sur un vrai MongoDB en replica set (Testcontainers) : mêmes cas que JdbcOutboxTests. */
@SpringBootTest(properties = {"teamslot.events.store=mongodb", "spring.flyway.enabled=false"})
@Import(MongoDeTest.class)
class MongoOutboxTests {

    @Autowired
    private Outbox outbox;

    @Autowired
    private MongoTemplate mongo;

    @Autowired
    private MongoTransactionManager transactionsMongo;

    @Autowired
    private EventReader lecteur;

    private TransactionTemplate transaction;

    @BeforeEach
    void viderLaCollection() {
        mongo.remove(new Query(), MongoOutbox.COLLECTION);
        transaction = new TransactionTemplate(transactionsMongo);
    }

    @Test
    void la_version_mongodb_est_utilisee() {
        assertThat(outbox).isInstanceOf(MongoOutbox.class);
    }

    @Test
    void record_ecrit_l_enveloppe_complete() {
        UUID matchId = UUID.randomUUID();

        transaction.executeWithoutResult(status ->
                outbox.record(matchId, "match.created", Map.of("matchId", matchId.toString())));

        List<Document> lignes = mongo.findAll(Document.class, MongoOutbox.COLLECTION);
        assertThat(lignes).hasSize(1);
        Document ligne = lignes.get(0);
        assertThat(ligne.getString("aggregateId")).isEqualTo(matchId.toString());
        assertThat(ligne.getString("eventType")).isEqualTo("match.created");
        assertThat(ligne.get("publishedAt")).isNull();
        assertThat(ligne.get("position")).isNotNull();

        // Enveloppe identique à celle de PostgreSQL : les autres services ne voient aucune différence
        String payload = ligne.getString("payload");
        EventEnvelope<JsonNode> enveloppe = lecteur.lire(payload);
        assertThat(enveloppe.eventId()).hasToString(ligne.getString("_id"));
        assertThat(enveloppe.type()).isEqualTo("match.created");
        assertThat(enveloppe.producer()).isEqualTo("events-test");
        assertThat(enveloppe.version()).isEqualTo(1);
        assertThat(payload).containsPattern("\"occurredAt\"\\s*:\\s*\"[^\"]+T[^\"]+Z\"");
        assertThat(enveloppe.data().get("matchId").toString()).contains(matchId.toString());
    }

    @Test
    void transaction_annulee_aucun_evenement() {
        transaction.executeWithoutResult(status -> {
            outbox.record(UUID.randomUUID(), "match.created", Map.of());
            status.setRollbackOnly();
        });

        assertThat(nombreEvenements()).isZero();
    }

    @Test
    void refuse_un_appel_hors_transaction() {
        UUID matchId = UUID.randomUUID();
        Map<String, String> data = Map.of();

        assertThatThrownBy(() -> outbox.record(matchId, "match.created", data))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(nombreEvenements()).isZero();
    }

    private long nombreEvenements() {
        return mongo.getCollection(MongoOutbox.COLLECTION).countDocuments();
    }
}
