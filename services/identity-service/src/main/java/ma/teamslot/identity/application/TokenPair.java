package ma.teamslot.identity.application;

/** Jeton d'accès, jeton de renouvellement et durée de validité du jeton d'accès en secondes. */
public record TokenPair(String accessToken, String refreshToken, long expiresIn) {
}
