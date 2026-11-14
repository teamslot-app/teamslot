package ma.teamslot.identity.adapter.out.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

import ma.teamslot.identity.application.LimiteRenvoiCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * SCRUM-133 — limite des renvois de code, par e-mail : 1 par minute et 5 par heure.
 * email-resend-min:{sha256(email)} -> présent pendant 1 minute après un envoi
 * email-resend-h:{sha256(email)}   -> compteur d'envois, remis à zéro après 1 heure
 * L'e-mail n'est jamais écrit en clair dans Redis : seulement son empreinte.
 */
@Component
public class RedisLimiteRenvoiCode implements LimiteRenvoiCode {

    static final int ENVOIS_PAR_HEURE = 5;
    private static final String MINUTE = "email-resend-min:";
    private static final String HEURE = "email-resend-h:";

    private final StringRedisTemplate redis;

    public RedisLimiteRenvoiCode(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean autoriser(String email) {
        String cle = empreinte(email);
        Boolean premierDeLaMinute = redis.opsForValue().setIfAbsent(MINUTE + cle, "1", Duration.ofMinutes(1));
        if (!Boolean.TRUE.equals(premierDeLaMinute)) {
            return false;
        }
        Long envois = redis.opsForValue().increment(HEURE + cle);
        if (envois != null && envois == 1L) {
            redis.expire(HEURE + cle, Duration.ofHours(1));
        }
        return envois != null && envois <= ENVOIS_PAR_HEURE;
    }

    private static String empreinte(String email) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(email.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
