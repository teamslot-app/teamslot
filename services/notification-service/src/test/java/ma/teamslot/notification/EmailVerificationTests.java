package ma.teamslot.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * SCRUM-134, avec un vrai Kafka, une vraie base et un vrai serveur SMTP de test (Mailpit) :
 * l'e-mail arrive (texte et HTML), un événement reçu deux fois n'envoie qu'un e-mail, le nom affiché est échappé
 * dans le HTML, un code expiré n'est pas envoyé, et ni le code ni l'adresse n'apparaissent dans les logs.
 */
@SpringBootTest(properties = {
    "teamslot.notification.consumers.enabled=true",
    "spring.mail.properties.mail.smtp.auth=false",
    "spring.mail.properties.mail.smtp.starttls.enable=false",
    "spring.mail.properties.mail.smtp.starttls.required=false"
})
@ActiveProfiles("test")
@Import({ConteneurKafka.class, EmailVerificationTests.Topics.class})
@ExtendWith(OutputCaptureExtension.class)
class EmailVerificationTests {

    private static final String TOPIC = "identity.email_verification_requested";
    private static final GenericContainer<?> MAILPIT =
            new GenericContainer<>("axllent/mailpit:v1.33.1").withExposedPorts(1025, 8025);

    static {
        MAILPIT.start();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Topics {
        @Bean
        NewTopic verificationDemandee() {
            return TopicBuilder.name(TOPIC).partitions(1).build();
        }
    }

    @DynamicPropertySource
    static void smtp(DynamicPropertyRegistry proprietes) {
        proprietes.add("spring.mail.host", MAILPIT::getHost);
        proprietes.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired KafkaContainer kafka;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void repartirDeZero() throws Exception {
        jdbc.sql("DELETE FROM processed_event").update();
        http.send(HttpRequest.newBuilder(api("/api/v1/messages")).DELETE().build(), HttpResponse.BodyHandlers.discarding());
    }

    @Test
    void l_email_arrive_avec_le_code_en_texte_et_en_html() throws Exception {
        envoyer(evenement(UUID.randomUUID(), "yassir@example.com", "Yassir", "482913", dansMinutes(15)));

        Attente.jusqua(() -> total() == 1);
        JsonNode resume = messages().get(0);
        assertThat(resume.get("Subject").asString()).isEqualTo("Ton code TeamSlot");
        assertThat(resume.get("To").get(0).get("Address").asString()).isEqualTo("yassir@example.com");
        JsonNode detail = message(resume.get("ID").asString());
        assertThat(detail.get("Text").asString()).contains("Bonjour Yassir").contains("482913");
        assertThat(detail.get("HTML").asString()).contains("482913");
    }

    @Test
    void le_meme_evenement_recu_deux_fois_n_envoie_qu_un_email() throws Exception {
        String message = evenement(UUID.randomUUID(), "double@example.com", "Double", "111222", dansMinutes(15));
        envoyer(message);
        envoyer(message);
        // Témoin : quand il est arrivé, les deux messages précédents ont été traités (une seule partition)
        envoyer(evenement(UUID.randomUUID(), "temoin@example.com", "Témoin", "333444", dansMinutes(15)));

        Attente.jusqua(() -> destinataires().contains("temoin@example.com"));
        assertThat(destinataires()).containsExactlyInAnyOrder("double@example.com", "temoin@example.com");
    }

    @Test
    void le_nom_affiche_est_echappe_dans_le_html() throws Exception {
        envoyer(evenement(UUID.randomUUID(), "pirate@example.com", "<b>Pirate</b>", "555666", dansMinutes(15)));

        Attente.jusqua(() -> total() == 1);
        String html = message(messages().get(0).get("ID").asString()).get("HTML").asString();
        assertThat(html).contains("&lt;b&gt;Pirate&lt;/b&gt;").doesNotContain("<b>Pirate</b>");
    }

    @Test
    void un_code_deja_expire_n_est_pas_envoye() throws Exception {
        envoyer(evenement(UUID.randomUUID(), "expire@example.com", "Expire", "777888", Instant.now().minusSeconds(60)));
        envoyer(evenement(UUID.randomUUID(), "temoin2@example.com", "Témoin", "999000", dansMinutes(15)));

        Attente.jusqua(() -> destinataires().contains("temoin2@example.com"));
        assertThat(destinataires()).containsExactly("temoin2@example.com");
    }

    @Test
    void ni_le_code_ni_l_adresse_dans_les_logs_meme_pour_un_message_invalide(CapturedOutput sortie) throws Exception {
        envoyer(evenement(UUID.randomUUID(), "secret@example.com", "Secret", "246810", dansMinutes(15)));
        // Code invalide (pas 6 chiffres) : message mis de côté dans le .dlt, sans nouvel essai
        envoyer(evenement(UUID.randomUUID(), "invalide@example.com", "Invalide", "12ab56", dansMinutes(15)));
        envoyer(evenement(UUID.randomUUID(), "temoin3@example.com", "Témoin", "135790", dansMinutes(15)));

        Attente.jusqua(() -> destinataires().contains("temoin3@example.com"));
        assertThat(destinataires()).containsExactlyInAnyOrder("secret@example.com", "temoin3@example.com");
        assertThat(lire(TOPIC + ".dlt")).anyMatch(r -> r.value().contains("invalide@example.com"));
        assertThat(sortie.getAll())
                .contains("E-mail de vérification envoyé")
                .doesNotContain("246810").doesNotContain("secret@example.com")
                .doesNotContain("12ab56").doesNotContain("invalide@example.com")
                .doesNotContain("135790").doesNotContain("temoin3@example.com");
    }

    // ---------- outils ----------

    private static Instant dansMinutes(int minutes) {
        return Instant.now().plusSeconds(60L * minutes);
    }

    private static String evenement(UUID eventId, String email, String nom, String code, Instant expire) {
        return """
                {"eventId":"%s","type":"%s","version":1,"occurredAt":"2026-11-14T22:00:00Z",
                 "producer":"identity-service","data":{"userId":"%s","email":"%s","displayName":"%s",
                 "code":"%s","expiresAt":"%s"}}
                """.formatted(eventId, TOPIC, UUID.randomUUID(), email, nom, code, expire);
    }

    private void envoyer(String message) throws Exception {
        kafkaTemplate.send(TOPIC, "cle", message).get(10, TimeUnit.SECONDS);
    }

    private URI api(String chemin) {
        return URI.create("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025) + chemin);
    }

    private JsonNode lireJson(String chemin) {
        try {
            HttpResponse<String> reponse = http.send(HttpRequest.newBuilder(api(chemin)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return json.readTree(reponse.body());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private int total() {
        return lireJson("/api/v1/messages").get("total").asInt();
    }

    private JsonNode messages() {
        return lireJson("/api/v1/messages").get("messages");
    }

    private JsonNode message(String id) {
        return lireJson("/api/v1/message/" + id);
    }

    private List<String> destinataires() {
        List<String> adresses = new ArrayList<>();
        messages().forEach(m -> adresses.add(m.get("To").get(0).get("Address").asString()));
        return adresses;
    }

    /** Lit un topic depuis le début, 20 secondes au maximum. */
    private List<ConsumerRecord<String, String>> lire(String topic) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "lecture-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> recus = new ArrayList<>();
        try (KafkaConsumer<String, String> lecteur = new KafkaConsumer<>(config)) {
            lecteur.subscribe(List.of(topic));
            long fin = System.currentTimeMillis() + 20_000;
            while (recus.isEmpty() && System.currentTimeMillis() < fin) {
                lecteur.poll(Duration.ofMillis(500)).forEach(recus::add);
            }
        }
        return recus;
    }
}
