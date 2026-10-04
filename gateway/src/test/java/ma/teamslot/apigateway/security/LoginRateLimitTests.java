package ma.teamslot.apigateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * SCRUM-67 — test de sécurité S4 : force brute sur la connexion.
 * Limite réduite à 3 tentatives pour que le test soit rapide.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "teamslot.rate-limit.login.max-attempts=3",
                "teamslot.rate-limit.login.window-seconds=60"
        })
class LoginRateLimitTests {

    @Value("${local.server.port}")
    int port;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void S4_forceBrute_bloqueeApresLaLimite() {
        // les 3 premières tentatives passent la passerelle (identity n'est pas lancé : pas 200,
        // mais surtout pas 429)
        for (int i = 0; i < 3; i++) {
            client.post().uri("/api/v1/auth/login").exchange()
                    .expectStatus().value(status -> assertThat(status).isNotEqualTo(429));
        }
        // la 4e est bloquée par la passerelle elle-même
        client.post().uri("/api/v1/auth/login").exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "60")
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.status").isEqualTo(429);
    }

    @Test
    void lesAutresRoutesNeSontPasLimitees() {
        for (int i = 0; i < 10; i++) {
            client.get().uri("/actuator/health").exchange().expectStatus().isOk();
        }
    }

    // ---------- tests unitaires du compteur, avec une horloge contrôlée ----------

    @Test
    void laFenetreSeReinitialiseApresLeDelai() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-03T12:00:00Z"));
        LoginRateLimitFilter filter = new LoginRateLimitFilter(2, Duration.ofSeconds(60), clock);

        assertThat(filter.tryAcquire("1.2.3.4")).isTrue();
        assertThat(filter.tryAcquire("1.2.3.4")).isTrue();
        assertThat(filter.tryAcquire("1.2.3.4")).isFalse();

        clock.advance(Duration.ofSeconds(61));
        assertThat(filter.tryAcquire("1.2.3.4")).isTrue();
    }

    @Test
    void chaqueAdresseASonPropreCompteur() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-03T12:00:00Z"));
        LoginRateLimitFilter filter = new LoginRateLimitFilter(1, Duration.ofSeconds(60), clock);

        assertThat(filter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(filter.tryAcquire("1.1.1.1")).isFalse();
        assertThat(filter.tryAcquire("2.2.2.2")).isTrue();
    }

    /** Horloge qu'on peut avancer à la main dans les tests. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
