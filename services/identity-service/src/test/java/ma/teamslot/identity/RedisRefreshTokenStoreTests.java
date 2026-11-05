package ma.teamslot.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import ma.teamslot.identity.application.RefreshTokenStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** SCRUM-110 : clé refresh:{hash}, TTL, expiration, consommation unique, révocation. */
@SpringBootTest
@ActiveProfiles("test")
class RedisRefreshTokenStoreTests {

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        RedisTestContainer.register(registry);
    }

    @Autowired
    RefreshTokenStore store;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void jetonStockeSousSonHashAvecUnTtl() {
        String hash = "hash-" + UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        store.save(hash, userId, Duration.ofDays(30));

        assertThat(redis.opsForValue().get("refresh:" + hash)).isEqualTo(userId.toString());
        assertThat(redis.getExpire("refresh:" + hash)).isPositive().isLessThanOrEqualTo(Duration.ofDays(30).toSeconds());
    }

    @Test
    void jetonExpire_neSeConsommePlus() throws Exception {
        String hash = "hash-" + UUID.randomUUID();
        store.save(hash, UUID.randomUUID(), Duration.ofSeconds(1));
        Thread.sleep(1500);

        assertThat(store.consume(hash, Duration.ofDays(30))).isEmpty();
        assertThat(store.findUsed(hash)).isEmpty();
    }

    @Test
    void jetonConsomme_estRevoque_etSaReutilisationEstDetectee() {
        String hash = "hash-" + UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        store.save(hash, userId, Duration.ofDays(30));

        assertThat(store.consume(hash, Duration.ofDays(30))).contains(userId);
        assertThat(store.consume(hash, Duration.ofDays(30))).isEmpty();
        assertThat(store.findUsed(hash)).contains(userId);
    }

    @Test
    void revoquerTous_supprimeLesJetonsActifsDeLUtilisateur() {
        UUID userId = UUID.randomUUID();
        String first = "hash-" + UUID.randomUUID();
        String second = "hash-" + UUID.randomUUID();
        store.save(first, userId, Duration.ofDays(30));
        store.save(second, userId, Duration.ofDays(30));

        store.revokeAllForUser(userId);

        assertThat(store.consume(first, Duration.ofDays(30))).isEmpty();
        assertThat(store.consume(second, Duration.ofDays(30))).isEmpty();
    }
}
