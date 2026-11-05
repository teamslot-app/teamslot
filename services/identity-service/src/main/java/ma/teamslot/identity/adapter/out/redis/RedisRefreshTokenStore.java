package ma.teamslot.identity.adapter.out.redis;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import ma.teamslot.identity.application.RefreshTokenStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Jetons de renouvellement dans Redis (SCRUM-110, ADR-017).
 * refresh:{hash}      -> identifiant utilisateur, TTL = durée de vie du jeton
 * refresh-used:{hash} -> identifiant utilisateur : jeton déjà consommé (détection de réutilisation)
 * user-refresh:{id}   -> ensemble des empreintes actives de l'utilisateur (pour tout révoquer)
 */
@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String ACTIVE = "refresh:";
    private static final String USED = "refresh-used:";
    private static final String BY_USER = "user-refresh:";

    private final StringRedisTemplate redis;

    public RedisRefreshTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(String tokenHash, UUID userId, Duration ttl) {
        redis.opsForValue().set(ACTIVE + tokenHash, userId.toString(), ttl);
        String index = BY_USER + userId;
        redis.opsForSet().add(index, tokenHash);
        redis.expire(index, ttl);
    }

    @Override
    public Optional<UUID> consume(String tokenHash, Duration usedMarkerTtl) {
        // GETDEL : lecture et suppression atomiques, deux rafraîchissements simultanés ne passent pas tous les deux
        String value = redis.opsForValue().getAndDelete(ACTIVE + tokenHash);
        if (value == null) {
            return Optional.empty();
        }
        UUID userId = UUID.fromString(value);
        redis.opsForValue().set(USED + tokenHash, value, usedMarkerTtl);
        redis.opsForSet().remove(BY_USER + userId, tokenHash);
        return Optional.of(userId);
    }

    @Override
    public Optional<UUID> findUsed(String tokenHash) {
        return Optional.ofNullable(redis.opsForValue().get(USED + tokenHash)).map(UUID::fromString);
    }

    @Override
    public void revokeAllForUser(UUID userId) {
        String index = BY_USER + userId;
        Set<String> hashes = redis.opsForSet().members(index);
        if (hashes != null && !hashes.isEmpty()) {
            redis.delete(hashes.stream().map(hash -> ACTIVE + hash).toList());
        }
        redis.delete(index);
    }
}
