package ma.teamslot.identity.application;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import ma.teamslot.events.Outbox;
import ma.teamslot.identity.adapter.out.persistence.EmailVerificationEntity;
import ma.teamslot.identity.adapter.out.persistence.EmailVerificationRepository;
import ma.teamslot.identity.adapter.out.persistence.UserEntity;
import ma.teamslot.identity.adapter.out.persistence.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * SCRUM-133 — vérification de l'e-mail par un code à 6 chiffres (contrat identity.yaml 2.1.0).
 * Le code est tiré avec SecureRandom, gardé haché (BCrypt, comparaison en temps constant), valable 15 minutes,
 * 5 essais au plus, à usage unique. Il ne part que dans l'événement identity.email_verification_requested
 * (outbox) : jamais dans un log ni dans une réponse HTTP.
 */
@Service
public class VerificationEmailService {

    public static final String EVENEMENT = "identity.email_verification_requested";
    static final Duration VALIDITE = Duration.ofMinutes(15);
    static final int ESSAIS_MAX = 5;

    /** Résultat d'une vérification ; le contrôleur le traduit en réponse HTTP (204, 400, 410, 429). */
    public enum Resultat { VERIFIE, CODE_INVALIDE, CODE_EXPIRE, TROP_D_ESSAIS }

    /** Données de l'événement : champs du catalogue docs/events.md (1.5). */
    public record CodeDemande(UUID userId, String email, String displayName, String code, Instant expiresAt) {
    }

    private final UserRepository users;
    private final EmailVerificationRepository verifications;
    private final PasswordEncoder encodeur;
    private final Outbox outbox;
    private final LimiteRenvoiCode limite;
    private final SecureRandom aleatoire = new SecureRandom();
    private final Clock horloge = Clock.systemUTC();
    /** Empreinte factice : comparée quand il n'y a pas de code, pour que la réponse prenne le même temps. */
    private final String empreinteFactice;

    public VerificationEmailService(UserRepository users, EmailVerificationRepository verifications,
                                    PasswordEncoder encodeur, Outbox outbox, LimiteRenvoiCode limite) {
        this.users = users;
        this.verifications = verifications;
        this.encodeur = encodeur;
        this.outbox = outbox;
        this.limite = limite;
        this.empreinteFactice = encodeur.encode("000000-" + UUID.randomUUID());
    }

    /** Crée (ou remplace) le code du compte et publie l'événement, dans la transaction de l'appelant. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void envoyerUnCode(UserEntity user) {
        creerEtPublier(user);
    }

    /** Méthode privée (pas d'appel interne à une méthode @Transactional : piège du proxy Spring, voir SCRUM-89). */
    private void creerEtPublier(UserEntity user) {
        String code = "%06d".formatted(aleatoire.nextInt(1_000_000));
        Instant maintenant = Instant.now(horloge);
        Instant echeance = maintenant.plus(VALIDITE);
        String empreinte = encodeur.encode(code);

        EmailVerificationEntity verification = verifications.findById(user.getId())
                .map(v -> {
                    v.remplacer(empreinte, echeance, maintenant);
                    return v;
                })
                .orElseGet(() -> new EmailVerificationEntity(user.getId(), empreinte, echeance, maintenant));
        verifications.save(verification);

        outbox.record(user.getId(), EVENEMENT,
                new CodeDemande(user.getId(), user.getEmail(), user.getDisplayName(), code, echeance));
    }

    /**
     * Vérifie le code. Un e-mail inconnu, déjà vérifié ou sans code en cours donne CODE_INVALIDE, comme un mauvais
     * code : la réponse ne dit pas si le compte existe. Un mauvais essai est compté (pas d'exception : la
     * transaction est validée, l'essai reste compté).
     */
    @Transactional
    public Resultat verifier(String emailSaisi, String code) {
        Optional<UserEntity> user = users.findByEmail(normaliser(emailSaisi)).filter(u -> !u.isEmailVerified());
        Optional<EmailVerificationEntity> enCours = user.flatMap(u -> verifications.findForUpdate(u.getId()));
        if (enCours.isEmpty()) {
            encodeur.matches(code, empreinteFactice);
            return Resultat.CODE_INVALIDE;
        }
        EmailVerificationEntity verification = enCours.get();
        if (verification.getAttempts() >= ESSAIS_MAX) {
            return Resultat.TROP_D_ESSAIS;
        }
        if (!Instant.now(horloge).isBefore(verification.getExpiresAt())) {
            return Resultat.CODE_EXPIRE;
        }
        if (!encodeur.matches(code, verification.getCodeHash())) {
            verification.compterUnEssai();
            return Resultat.CODE_INVALIDE;
        }
        user.get().marquerEmailVerifie();
        verifications.delete(verification);
        return Resultat.VERIFIE;
    }

    /**
     * Renvoie un code si le compte existe et attend sa vérification, dans la limite d'envois. Ne dit jamais ce qui
     * s'est passé : le contrôleur répond 202 dans tous les cas.
     */
    @Transactional
    public void renvoyer(String emailSaisi) {
        String email = normaliser(emailSaisi);
        if (!limite.autoriser(email)) {
            return;
        }
        users.findByEmail(email)
                .filter(u -> !u.isEmailVerified())
                .ifPresent(this::creerEtPublier);
    }

    private static String normaliser(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
