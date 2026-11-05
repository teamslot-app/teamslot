package ma.teamslot.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

/** SCRUM-75 : venue.created apprend à payment le gérant de chaque terrain, une seule fois (vrai Kafka). */
@SpringBootTest(properties = "teamslot.payment.consumers.enabled=true")
@ActiveProfiles("test")
@Import(KafkaDeTest.class)
class VenueCreatedConsumerTests {

    private static final String TOPIC = "venue.created";

    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void viderLesTables() {
        jdbc.sql("DELETE FROM venue_owners").update();
        jdbc.sql("DELETE FROM processed_event").update();
    }

    @Test
    void venue_created_enregistre_le_gerant_du_terrain() throws Exception {
        UUID terrain = UUID.randomUUID();
        UUID gerant = UUID.randomUUID();

        envoyer(terrainCree(UUID.randomUUID(), terrain, "\"" + gerant + "\""));

        Attente.jusqua(() -> gerant(terrain) != null);
        assertThat(gerant(terrain)).isEqualTo(gerant);
    }

    @Test
    void le_meme_evenement_rejoue_et_un_autre_gerant_pour_le_meme_terrain_ne_changent_rien() throws Exception {
        UUID terrain = UUID.randomUUID();
        UUID gerant = UUID.randomUUID();
        String message = terrainCree(UUID.randomUUID(), terrain, "\"" + gerant + "\"");

        envoyer(message);
        envoyer(message);
        // Autre eventId, autre ownerId pour le même terrain : le premier gérant reste.
        envoyer(terrainCree(UUID.randomUUID(), terrain, "\"" + UUID.randomUUID() + "\""));
        UUID temoin = UUID.randomUUID();
        envoyer(terrainCree(UUID.randomUUID(), temoin, "\"" + UUID.randomUUID() + "\""));

        // Une seule partition : quand le témoin est enregistré, les trois messages précédents ont été traités.
        Attente.jusqua(() -> gerant(temoin) != null);
        assertThat(gerant(terrain)).isEqualTo(gerant);
        assertThat(jdbc.sql("SELECT count(*) FROM venue_owners").query(Long.class).single()).isEqualTo(2L);
    }

    @Test
    void un_venue_created_sans_gerant_est_refuse() throws Exception {
        UUID sansGerant = UUID.randomUUID();
        envoyer(terrainCree(UUID.randomUUID(), sansGerant, "null"));
        UUID temoin = UUID.randomUUID();
        envoyer(terrainCree(UUID.randomUUID(), temoin, "\"" + UUID.randomUUID() + "\""));

        Attente.jusqua(() -> gerant(temoin) != null);
        assertThat(gerant(sansGerant)).isNull();
    }

    /** venue.created (docs/events.md) ; payment ne lit que venueId et ownerId. */
    private static String terrainCree(UUID eventId, UUID terrain, String ownerJson) {
        return """
                {"eventId":"%s","type":"venue.created","version":1,"occurredAt":"2026-11-05T10:00:00Z",
                 "producer":"venue-service","data":{"venueId":"%s","ownerId":%s,"name":"Terrain Gueliz",
                 "latitude":31.63,"longitude":-8.01,"pricePerHour":{"amount":30000,"currency":"MAD"},
                 "acceptsOnSitePayment":true}}
                """.formatted(eventId, terrain, ownerJson);
    }

    private void envoyer(String message) throws Exception {
        kafkaTemplate.send(TOPIC, "cle", message).get(10, TimeUnit.SECONDS);
    }

    private UUID gerant(UUID terrain) {
        return jdbc.sql("SELECT owner_id FROM venue_owners WHERE venue_id = :v").param("v", terrain)
                .query(UUID.class).optional().orElse(null);
    }
}
