package ma.teamslot.identity.application;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Stockage des jetons de renouvellement : seule l'empreinte SHA-256 est connue du stockage. */
public interface RefreshTokenStore {

    /** Enregistre un jeton actif, qui expire tout seul après ttl. */
    void save(String tokenHash, UUID userId, Duration ttl);

    /**
     * Consomme un jeton actif de façon atomique : il n'est plus valide ensuite.
     * Retourne l'utilisateur, ou vide si le jeton est inconnu, expiré ou déjà consommé.
     */
    Optional<UUID> consume(String tokenHash, Duration usedMarkerTtl);

    /** Retourne l'utilisateur si ce jeton a déjà été consommé (réutilisation = vol possible). */
    Optional<UUID> findUsed(String tokenHash);

    /** Révoque tous les jetons encore actifs d'un utilisateur. */
    void revokeAllForUser(UUID userId);
}
