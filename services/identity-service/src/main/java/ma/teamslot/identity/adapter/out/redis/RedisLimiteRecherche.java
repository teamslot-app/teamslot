package ma.teamslot.identity.adapter.out.redis;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import ma.teamslot.identity.application.LimiteRecherche;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * SCRUM-82 — 30 recherches de joueurs par minute et par compte.
 * user-search:{id du compte} -> compteur, remis à zéro 1 minute après la première recherche de la fenêtre.
 */
@Component
public class RedisLimiteRecherche implements LimiteRecherche {

    static final int RECHERCHES_PAR_MINUTE = 30;
    private static final Duration FENETRE = Duration.ofMinutes(1);
    private static final String CLE = "user-search:";

    private final StringRedisTemplate redis;

    public RedisLimiteRecherche(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long attendreAvantRecherche(UUID compte) {
        String cle = CLE + compte;
        Long recherches = redis.opsForValue().increment(cle);
        if (recherches != null && recherches == 1L) {
            redis.expire(cle, FENETRE);
        }
        if (recherches == null || recherches <= RECHERCHES_PAR_MINUTE) {
            return 0;
        }
        Long reste = redis.getExpire(cle, TimeUnit.SECONDS);
        if (reste == null || reste < 0) {
            // compteur sans durée de vie (arrêt entre INCR et EXPIRE) : on la remet, sinon le blocage serait définitif
            redis.expire(cle, FENETRE);
            return FENETRE.toSeconds();
        }
        return Math.max(1, reste);
    }
}
