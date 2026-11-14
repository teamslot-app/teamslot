package ma.teamslot.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.kafka.KafkaContainer;

/**
 * SCRUM-81, avec un vrai Kafka et une vraie base : critère 1 (participant_added et confirmed consommés,
 * notification enregistrée pour la liste « Mes notifications »), critère 2 (un événement reçu deux fois
 * = une seule notification), critère 3 (rien pour un invité sans compte).
 */
@SpringBootTest(properties = "teamslot.notification.consumers.enabled=true")
@ActiveProfiles("test")
@Import({ConteneurKafka.class, NotificationConsumerTests.Topics.class})
class NotificationConsumerTests {

    /** Topics créés au démarrage (comme create-topics.sh en staging) : les consommateurs les trouvent tout de suite. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Topics {
        @Bean
        NewTopic participantAjoute() {
            return TopicBuilder.name("match.participant_added").partitions(1).build();
        }

        @Bean
        NewTopic demandeAcceptee() {
            return TopicBuilder.name("match.join_accepted").partitions(1).build();
        }

        @Bean
        NewTopic matchConfirme() {
            return TopicBuilder.name("match.confirmed").partitions(1).build();
        }
    }

    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired KafkaContainer kafka;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void repartirDeZero() {
        jdbc.sql("DELETE FROM notification").update();
        jdbc.sql("DELETE FROM match_participant").update();
        jdbc.sql("DELETE FROM processed_event").update();
    }

    @Test
    void critere_1_ajout_d_un_compte_cree_sa_notification() throws Exception {
        UUID match = UUID.randomUUID();
        UUID joueur = UUID.randomUUID();

        envoyer("match.participant_added", participantAjoute(UUID.randomUUID(), match, joueur, null));

        Attente.jusqua(() -> notifications(joueur, "MATCH_PARTICIPANT_ADDED") == 1);
        assertThat(jdbc.sql("SELECT count(*) FROM notification WHERE user_id = :u AND match_id = :m AND read_at IS NULL")
                .param("u", joueur).param("m", match).query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void critere_2_le_meme_evenement_recu_deux_fois_ne_cree_qu_une_notification() throws Exception {
        UUID match = UUID.randomUUID();
        UUID joueur = UUID.randomUUID();
        String message = participantAjoute(UUID.randomUUID(), match, joueur, null);

        envoyer("match.participant_added", message);
        envoyer("match.participant_added", message);
        // Témoin : quand il est traité, les deux messages précédents l'ont été (une seule partition)
        UUID temoin = UUID.randomUUID();
        envoyer("match.participant_added", participantAjoute(UUID.randomUUID(), UUID.randomUUID(), temoin, null));

        Attente.jusqua(() -> notifications(temoin, "MATCH_PARTICIPANT_ADDED") == 1);
        assertThat(notifications(joueur, "MATCH_PARTICIPANT_ADDED")).isEqualTo(1);
    }

    @Test
    void critere_3_rien_pour_un_invite_sans_compte() throws Exception {
        envoyer("match.participant_added",
                participantAjoute(UUID.randomUUID(), UUID.randomUUID(), null, "Yassir"));
        UUID temoin = UUID.randomUUID();
        envoyer("match.participant_added", participantAjoute(UUID.randomUUID(), UUID.randomUUID(), temoin, null));

        Attente.jusqua(() -> notifications(temoin, "MATCH_PARTICIPANT_ADDED") == 1);
        assertThat(jdbc.sql("SELECT count(*) FROM notification").query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql("SELECT count(*) FROM match_participant").query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void match_confirme_previent_chaque_compte_du_match() throws Exception {
        UUID match = UUID.randomUUID();
        UUID organisateurAjoute = UUID.randomUUID();
        UUID joueurAccepte = UUID.randomUUID();
        envoyer("match.participant_added", participantAjoute(UUID.randomUUID(), match, organisateurAjoute, null));
        envoyer("match.participant_added", participantAjoute(UUID.randomUUID(), match, null, "Invité"));
        envoyer("match.join_accepted", demandeAcceptee(UUID.randomUUID(), match, joueurAccepte));
        Attente.jusqua(() -> participants(match) == 2);

        envoyer("match.confirmed", matchConfirme(UUID.randomUUID(), match));

        Attente.jusqua(() -> notifications(joueurAccepte, "MATCH_CONFIRMED") == 1);
        assertThat(notifications(organisateurAjoute, "MATCH_CONFIRMED")).isEqualTo(1);
        assertThat(notifications(joueurAccepte, "MATCH_JOIN_ACCEPTED")).isEqualTo(1);
        // l'invité sans compte n'a rien : 2 notifications MATCH_CONFIRMED en tout
        assertThat(jdbc.sql("SELECT count(*) FROM notification WHERE type = 'MATCH_CONFIRMED'")
                .query(Long.class).single()).isEqualTo(2L);
    }

    @Test
    void donnees_invalides_vont_au_dlt_sans_notification() throws Exception {
        // userId et guestName à la fois : interdit par le contrat de l'événement
        envoyer("match.participant_added",
                participantAjoute(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Les deux"));
        UUID temoin = UUID.randomUUID();
        envoyer("match.participant_added", participantAjoute(UUID.randomUUID(), UUID.randomUUID(), temoin, null));

        Attente.jusqua(() -> notifications(temoin, "MATCH_PARTICIPANT_ADDED") == 1);
        assertThat(jdbc.sql("SELECT count(*) FROM notification").query(Long.class).single()).isEqualTo(1L);
        assertThat(lire("match.participant_added.dlt", 1)).isNotEmpty();
    }

    private static String enveloppe(UUID eventId, String type, String data) {
        return """
                {"eventId":"%s","type":"%s","version":1,"occurredAt":"2026-11-20T18:00:00Z",
                 "producer":"match-service","data":%s}
                """.formatted(eventId, type, data);
    }

    private static String participantAjoute(UUID eventId, UUID match, UUID compte, String surnom) {
        String data = "{\"matchId\":\"%s\",\"participantId\":\"%s\"%s%s}".formatted(match, UUID.randomUUID(),
                compte == null ? "" : ",\"userId\":\"" + compte + "\"",
                surnom == null ? "" : ",\"guestName\":\"" + surnom + "\"");
        return enveloppe(eventId, "match.participant_added", data);
    }

    private static String demandeAcceptee(UUID eventId, UUID match, UUID compte) {
        return enveloppe(eventId, "match.join_accepted",
                "{\"matchId\":\"%s\",\"userId\":\"%s\"}".formatted(match, compte));
    }

    private static String matchConfirme(UUID eventId, UUID match) {
        return enveloppe(eventId, "match.confirmed", "{\"matchId\":\"%s\"}".formatted(match));
    }

    private void envoyer(String topic, String message) throws Exception {
        kafkaTemplate.send(topic, "cle", message).get(10, TimeUnit.SECONDS);
    }

    private long notifications(UUID joueur, String type) {
        return jdbc.sql("SELECT count(*) FROM notification WHERE user_id = :u AND type = :t")
                .param("u", joueur).param("t", type).query(Long.class).single();
    }

    private long participants(UUID match) {
        return jdbc.sql("SELECT count(*) FROM match_participant WHERE match_id = :m")
                .param("m", match).query(Long.class).single();
    }

    /** Lit un topic depuis le début jusqu'à avoir le nombre attendu de messages, 20 secondes au maximum. */
    private List<ConsumerRecord<String, String>> lire(String topic, int attendus) {
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
            while (recus.size() < attendus && System.currentTimeMillis() < fin) {
                lecteur.poll(Duration.ofMillis(500)).forEach(recus::add);
            }
        }
        return recus;
    }
}
