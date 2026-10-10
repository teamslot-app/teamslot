package ma.teamslot.servicetemplate.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/** Lecture des messages reçus : valide, champ inconnu (règle d'évolution), invalide. */
@SpringBootTest
@ActiveProfiles("test")
class EventReaderTests {

    private static final String VALIDE = """
            {"eventId":"8c1f2a4e-6b1d-4f3a-9e2c-1d5b7a9c0e11","type":"match.created","version":1,
             "occurredAt":"2026-10-12T20:02:00Z","producer":"match-service",
             "data":{"matchId":"3f0c9b2a-7e41-4d8a-b1c6-2a9e5f7d4c10"}}
            """;

    @Autowired
    private EventReader lecteur;

    @Test
    void lit_une_enveloppe_valide() {
        EventEnvelope<JsonNode> enveloppe = lecteur.lire(VALIDE);

        assertThat(enveloppe.eventId()).isEqualTo(UUID.fromString("8c1f2a4e-6b1d-4f3a-9e2c-1d5b7a9c0e11"));
        assertThat(enveloppe.type()).isEqualTo("match.created");
        assertThat(enveloppe.occurredAt()).isEqualTo(Instant.parse("2026-10-12T20:02:00Z"));
        assertThat(enveloppe.data().has("matchId")).isTrue();
    }

    @Test
    void ignore_un_champ_inconnu_regle_d_evolution() {
        String avecNouveauChamp = VALIDE.replace("\"version\":1,", "\"version\":1,\"nouveauChamp\":true,");

        assertThat(lecteur.lire(avecNouveauChamp).type()).isEqualTo("match.created");
    }

    @Test
    void refuse_un_json_invalide() {
        String casse = "{ceci n'est pas du json";

        assertThatThrownBy(() -> lecteur.lire(casse)).isInstanceOf(JacksonException.class);
    }

    @Test
    void refuse_une_enveloppe_sans_event_id() {
        String sansEventId = VALIDE.replace("\"eventId\":\"8c1f2a4e-6b1d-4f3a-9e2c-1d5b7a9c0e11\",", "");

        assertThatThrownBy(() -> lecteur.lire(sansEventId)).isInstanceOf(JacksonException.class);
    }
}
