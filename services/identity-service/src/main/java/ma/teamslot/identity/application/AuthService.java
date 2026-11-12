package ma.teamslot.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import ma.teamslot.identity.adapter.out.persistence.UserEntity;
import ma.teamslot.identity.adapter.out.persistence.UserRepository;
import ma.teamslot.identity.domain.AccountType;
import ma.teamslot.identity.domain.IndicatifsPays;
import ma.teamslot.identity.domain.PlatformRole;
import ma.teamslot.identity.domain.PolitiqueMotDePasse;
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
    private final Clock clock = Clock.systemUTC();
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

    /** Données d'inscription ; email, birthDate et phoneCountry restent facultatifs pendant la migration (contrat 2.0.0). */
    public record Inscription(String phone, String rawPassword, String displayName, String email,
                              LocalDate birthDate, String phoneCountry, AccountType accountType) {

        /** Sans accountType (anciens clients) : compte joueur. */
        public Inscription(String phone, String rawPassword, String displayName, String email,
                           LocalDate birthDate, String phoneCountry) {
            this(phone, rawPassword, displayName, email, birthDate, phoneCountry, AccountType.PLAYER);
        }
    }

    private static final LocalDate NAISSANCE_MIN = LocalDate.of(1900, 1, 1);

    @Transactional
    public UserEntity register(Inscription demande) {
        String email = demande.email() == null ? null : demande.email().strip().toLowerCase(Locale.ROOT);
        String displayName = demande.displayName().strip();
        verifier(demande, email, displayName);

        // même erreur que ce soit le numéro ou l'e-mail : la réponse ne dit pas lequel existe déjà
        if (users.existsByPhone(demande.phone()) || (email != null && users.existsByEmail(email))) {
            throw new AuthErrors.AccountAlreadyExists();
        }
        UserEntity user = new UserEntity(UUID.randomUUID(), demande.phone(), passwordEncoder.encode(demande.rawPassword()),
                displayName, PlatformRole.PLAYER, Instant.now(), email, demande.birthDate(), demande.phoneCountry());
        // SCRUM-136 : toujours PLAYER à l'inscription ; une demande de gérant attend un administrateur (SCRUM-137)
        if (demande.accountType() == AccountType.VENUE_MANAGER) {
            user.demanderAEtreGerant(Instant.now());
        }
        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // deux inscriptions simultanées avec le même numéro ou e-mail : les contraintes UNIQUE tranchent
            throw new AuthErrors.AccountAlreadyExists();
        }
        // TODO SCRUM-70 / SCRUM-133 : publier user.registered et identity.email_verification_requested par l'outbox
        return user;
    }

    /** Ancienne signature (apps/web, tests existants) : inscription sans e-mail ni date de naissance. */
    public UserEntity register(String phone, String rawPassword, String displayName) {
        return register(new Inscription(phone, rawPassword, displayName, null, null, null));
    }

    private void verifier(Inscription demande, String email, String displayName) {
        List<String> violations = PolitiqueMotDePasse.violations(demande.rawPassword(), email, displayName, demande.phone());
        if (!violations.isEmpty()) {
            throw new AuthErrors.WeakPassword(violations);
        }
        if (!IndicatifsPays.coherent(demande.phoneCountry(), demande.phone())) {
            throw new AuthErrors.PhoneCountryMismatch();
        }
        LocalDate naissance = demande.birthDate();
        if (naissance != null && (naissance.isBefore(NAISSANCE_MIN) || !naissance.isBefore(LocalDate.now(clock)))) {
            throw new AuthErrors.InvalidBirthDate();
        }
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
        return issueTokens(user.get().getId(), user.get().getPlatformRole());
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
            UserEntity owner = users.findById(userId.get()).orElseThrow(AuthErrors.InvalidRefreshToken::new);
            return issueTokens(owner.getId(), owner.getPlatformRole());
        }
        refreshTokens.findUsed(hash).ifPresent(refreshTokens::revokeAllForUser);
        throw new AuthErrors.InvalidRefreshToken();
    }

    private TokenPair issueTokens(UUID userId, PlatformRole role) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String rawRefreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(sha256(rawRefreshToken), userId, refreshTokenTtl);
        return new TokenPair(tokens.createAccessToken(userId, role), rawRefreshToken, tokens.accessTokenTtlSeconds());
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
