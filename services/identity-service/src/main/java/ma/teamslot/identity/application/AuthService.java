package ma.teamslot.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import ma.teamslot.identity.adapter.out.persistence.UserEntity;
import ma.teamslot.identity.adapter.out.persistence.UserRepository;
import ma.teamslot.identity.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SCRUM-57 — inscription, connexion et renouvellement des jetons. */
@Service
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenStore refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final Duration refreshTokenTtl;
    private final SecureRandom random = new SecureRandom();
    /** Empreinte factice : vérifiée quand le téléphone est inconnu, pour que la réponse prenne le même temps. */
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenStore refreshTokens,
                       PasswordEncoder passwordEncoder, TokenService tokens,
                       @Value("${teamslot.jwt.refresh-token-ttl-days:30}") long refreshTokenTtlDays) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.refreshTokenTtl = Duration.ofDays(refreshTokenTtlDays);
        this.dummyHash = passwordEncoder.encode("mot-de-passe-factice-" + UUID.randomUUID());
    }

    @Transactional
    public UserEntity register(String phone, String rawPassword, String displayName) {
        if (users.existsByPhone(phone)) {
            throw new AuthErrors.PhoneAlreadyUsed();
        }
        UserEntity user = new UserEntity(UUID.randomUUID(), phone, passwordEncoder.encode(rawPassword),
                displayName.strip(), PlatformRole.PLAYER, Instant.now());
        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // deux inscriptions simultanées avec le même numéro : la contrainte UNIQUE tranche
            throw new AuthErrors.PhoneAlreadyUsed();
        }
        // TODO SCRUM-70 : publier l'événement user.registered par l'outbox de Sara
        return user;
    }

    @Transactional
    public TokenPair login(String phone, String rawPassword) {
        Optional<UserEntity> user = users.findByPhone(phone);
        // on vérifie toujours un mot de passe, même si le téléphone est inconnu (pas d'énumération par le temps)
        boolean passwordMatches = passwordEncoder.matches(rawPassword,
                user.map(UserEntity::getPasswordHash).orElse(dummyHash));
        if (user.isEmpty() || !passwordMatches) {
            throw new AuthErrors.InvalidCredentials();
        }
        return issueTokens(user.get().getId());
    }

    /**
     * Rotation : le jeton présenté est consommé et remplacé.
     * S'il avait déjà été utilisé, quelqu'un l'a peut-être volé : tous les jetons de l'utilisateur sont révoqués.
     * Un jeton expiré ou inconnu n'existe plus dans Redis : même réponse, 401.
     */
    public TokenPair refresh(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);
        Optional<UUID> userId = refreshTokens.consume(hash, refreshTokenTtl);
        if (userId.isPresent()) {
            return issueTokens(userId.get());
        }
        refreshTokens.findUsed(hash).ifPresent(refreshTokens::revokeAllForUser);
        throw new AuthErrors.InvalidRefreshToken();
    }

    private TokenPair issueTokens(UUID userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String rawRefreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(sha256(rawRefreshToken), userId, refreshTokenTtl);
        return new TokenPair(tokens.createAccessToken(userId), rawRefreshToken, tokens.accessTokenTtlSeconds());
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
