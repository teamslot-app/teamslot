package ma.teamslot.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/** Critère Jira SCRUM-111 : avec MongoDB aussi, un événement reçu deux fois n'a qu'un seul effet. */
@SpringBootTest(properties = {"teamslot.events.store=mongodb", "spring.flyway.enabled=false"})
@Import(MongoDeTest.class)
class MongoIdempotentProcessorTests {

    @Autowired
    private IdempotentProcessor processeur;

    @Autowired
    private MongoTemplate mongo;

    @BeforeEach
    void viderLaCollection() {
        mongo.remove(new Query(), "processed_event");
    }

    @Test
    void la_version_mongodb_est_utilisee() {
        assertThat(processeur.getClass().getSimpleName()).isEqualTo("MongoIdempotentProcessor");
    }

    @Test
    void meme_evenement_recu_deux_fois_un_seul_effet() {
        UUID eventId = UUID.randomUUID();
        AtomicInteger effets = new AtomicInteger();

        boolean premier = processeur.traiterUneFois(eventId, effets::incrementAndGet);
        boolean second = processeur.traiterUneFois(eventId, effets::incrementAndGet);

        assertThat(premier).isTrue();
        assertThat(second).isFalse();
        assertThat(effets).hasValue(1);
        assertThat(mongo.getCollection("processed_event").countDocuments()).isEqualTo(1);
    }

    @Test
    void si_le_traitement_echoue_l_evenement_peut_etre_retraite() {
        UUID eventId = UUID.randomUUID();
        Runnable traitementEnPanne = () -> {
            throw new IllegalStateException("panne simulée");
        };

        assertThatThrownBy(() -> processeur.traiterUneFois(eventId, traitementEnPanne))
                .isInstanceOf(IllegalStateException.class);
        // La transaction est annulée : l'eventId n'a pas été gardé
        assertThat(mongo.getCollection("processed_event").countDocuments()).isZero();

        AtomicInteger effets = new AtomicInteger();
        assertThat(processeur.traiterUneFois(eventId, effets::incrementAndGet)).isTrue();
        assertThat(effets).hasValue(1);
    }
}
