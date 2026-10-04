package ma.teamslot.servicetemplate.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventEnvelopeTest {

    @Test
    void of_remplit_l_enveloppe() {
        Instant avant = Instant.now();

        EventEnvelope<Map<String, String>> enveloppe =
                EventEnvelope.of("match.created", "match-service", Map.of("matchId", "abc"));

        assertThat(enveloppe.eventId()).isNotNull();
        assertThat(enveloppe.type()).isEqualTo("match.created");
        assertThat(enveloppe.version()).isEqualTo(1);
        assertThat(enveloppe.producer()).isEqualTo("match-service");
        assertThat(enveloppe.occurredAt()).isAfterOrEqualTo(avant);
        assertThat(enveloppe.data()).containsEntry("matchId", "abc");
    }

    @Test
    void refuse_un_type_hors_format() {
        assertThatThrownBy(() -> EventEnvelope.of("Match.Created", "match-service", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EventEnvelope.of("matchcreated", "match-service", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toString_n_affiche_pas_les_donnees_personnelles() {
        EventEnvelope<Map<String, String>> enveloppe =
                EventEnvelope.of("match.participant_added", "match-service", Map.of("guestName", "Yassir"));

        assertThat(enveloppe.toString())
                .contains("match.participant_added")
                .doesNotContain("Yassir");
    }

    @Test
    void refuse_une_enveloppe_sans_data() {
        assertThatThrownBy(() ->
                new EventEnvelope<>(UUID.randomUUID(), "match.created", 1, Instant.now(), "match-service", null))
                .isInstanceOf(NullPointerException.class);
    }
}