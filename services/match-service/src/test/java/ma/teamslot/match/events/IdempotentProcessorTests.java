package ma.teamslot.match.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/** Critère Jira : un événement reçu deux fois n'a qu'un seul effet. */
@SpringBootTest
@ActiveProfiles("test")
class IdempotentProcessorTests {

    @Autowired
    private IdempotentProcessor processeur;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void viderLaTable() {
        jdbc.sql("DELETE FROM processed_event").update();
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
    }

    @Test
    void si_le_traitement_echoue_l_evenement_peut_etre_retraite() {
        UUID eventId = UUID.randomUUID();
        Runnable traitementEnPanne = () -> {
            throw new IllegalStateException("panne simulée");
        };

        assertThatThrownBy(() -> processeur.traiterUneFois(eventId, traitementEnPanne))
                .isInstanceOf(IllegalStateException.class);

        AtomicInteger effets = new AtomicInteger();
        assertThat(processeur.traiterUneFois(eventId, effets::incrementAndGet)).isTrue();
        assertThat(effets).hasValue(1);
    }
}
