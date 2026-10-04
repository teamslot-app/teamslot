package ma.teamslot.identity.application;

/** Erreurs métier d'identity-service, traduites en Problem Details par la couche web. */
public final class AuthErrors {

    private AuthErrors() {
    }

    /** Numéro de téléphone déjà associé à un compte (409). */
    public static final class PhoneAlreadyUsed extends RuntimeException {
        public PhoneAlreadyUsed() {
            super("phone already used");
        }
    }

    /** Téléphone inconnu ou mot de passe faux : même erreur dans les deux cas (401). */
    public static final class InvalidCredentials extends RuntimeException {
        public InvalidCredentials() {
            super("invalid credentials");
        }
    }

    /** Jeton de renouvellement inconnu, expiré, révoqué ou déjà utilisé (401). */
    public static final class InvalidRefreshToken extends RuntimeException {
        public InvalidRefreshToken() {
            super("invalid refresh token");
        }
    }
}
