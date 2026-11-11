package ma.teamslot.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** SCRUM-89 : identifiant de corrélation écrit dans l'enveloppe et remis dans le MDC par le consommateur. */
class CorrelationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ID = "7d9f3a2e-1b4c-4e8f-9a6d-2c5b8e1f0a37";

    @AfterEach
    void viderLeMdc() {
        MDC.clear();
    }

    // Écriture : rempli depuis le MDC

    @Test
    void of_prend_l_identifiant_de_la_requete_en_cours() {
        MDC.put(Correlation.MDC_KEY, ID);

        EventEnvelope<Map<String, String>> enveloppe = EventEnvelope.of("match.created", "match-service", Map.of());

        assertThat(enveloppe.correlationId()).isEqualTo(ID);
        assertThat(JSON.writeValueAsString(enveloppe)).contains("\"correlationId\":\"" + ID + "\"");
    }

    @Test
    void sans_requete_le_champ_est_absent_du_json() {
        EventEnvelope<Map<String, String>> enveloppe = EventEnvelope.of("match.created", "match-service", Map.of());

        assertThat(enveloppe.correlationId()).isNull();
        assertThat(JSON.writeValueAsString(enveloppe)).doesNotContain("correlationId");
    }

    @Test
    void une_valeur_du_mdc_qui_n_est_pas_un_uuid_n_est_pas_recopiee() {
        MDC.put(Correlation.MDC_KEY, "abc\nFAUSSE LIGNE DE LOG");

        assertThat(EventEnvelope.of("match.created", "match-service", Map.of()).correlationId()).isNull();
    }

    // Lecture : ancien format accepté, valeur invalide ignorée sans rejeter l'événement

    @Test
    void lit_une_enveloppe_de_l_ancien_format_sans_le_champ() {
        String ancien = """
                {"eventId":"%s","type":"match.created","version":1,"occurredAt":"2026-11-11T10:00:00Z",
                 "producer":"match-service","data":{"matchId":"m1"}}
                """.formatted(UUID.randomUUID());

        EventEnvelope<JsonNode> enveloppe = new EventReader(JSON).lire(ancien);

        assertThat(enveloppe.correlationId()).isNull();
        assertThat(enveloppe.type()).isEqualTo("match.created");
    }

    @Test
    void lit_l_identifiant_et_ignore_une_valeur_qui_n_est_pas_un_uuid() {
        String modele = """
                {"eventId":"%s","type":"match.created","version":1,"occurredAt":"2026-11-11T10:00:00Z",
                 "producer":"match-service","data":{},"correlationId":"%s"}
                """;
        EventReader lecteur = new EventReader(JSON);

        assertThat(lecteur.lire(modele.formatted(UUID.randomUUID(), ID)).correlationId()).isEqualTo(ID);
        EventEnvelope<JsonNode> piege = lecteur.lire(modele.formatted(UUID.randomUUID(), "x\\r\\nINJECTION"));
        assertThat(piege.correlationId()).isNull();
        assertThat(piege.type()).isEqualTo("match.created"); // l'événement n'est pas rejeté
    }

    // Consommateur : MDC rempli pendant le traitement, puis retiré

    @Test
    void le_consommateur_met_l_identifiant_dans_le_mdc_pendant_le_traitement_puis_le_retire() {
        AtomicReference<String> vu = new AtomicReference<>();
        IdempotentProcessor processeur = (eventId, traitement) -> {
            traitement.run();
            return true;
        };
        EventEnvelope<Map<String, String>> evenement = new EventEnvelope<>(UUID.randomUUID(), "payment.completed", 1,
                java.time.Instant.now(), "payment-service", Map.of(), ID);

        boolean execute = processeur.traiterUneFois(evenement, () -> vu.set(MDC.get(Correlation.MDC_KEY)));

        assertThat(execute).isTrue();
        assertThat(vu.get()).isEqualTo(ID);
        assertThat(MDC.get(Correlation.MDC_KEY)).isNull();
    }

    @Test
    void la_valeur_d_avant_est_remise_meme_si_le_traitement_echoue() {
        MDC.put(Correlation.MDC_KEY, "11111111-1111-4111-8111-111111111111");

        assertThatThrownBy(() -> Correlation.pendant(ID, () -> {
            throw new IllegalStateException("échec du traitement");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(Correlation.MDC_KEY)).isEqualTo("11111111-1111-4111-8111-111111111111");
    }

    @Test
    void sans_identifiant_le_traitement_s_execute_quand_meme() {
        AtomicReference<String> vu = new AtomicReference<>("pas appelé");

        Correlation.pendant(null, () -> vu.set(MDC.get(Correlation.MDC_KEY)));

        assertThat(vu.get()).isNull();
    }
}
