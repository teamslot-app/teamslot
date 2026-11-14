package ma.teamslot.identity.application;

/** Erreurs métier d'identity-service, traduites en Problem Details par la couche web. */
public final class AuthErrors {

    private AuthErrors() {
    }

    /** Numéro ou e-mail déjà associé à un compte (409) : le message ne dit pas lequel des deux. */
    public static final class AccountAlreadyExists extends RuntimeException {
        public AccountAlreadyExists() {
            super("account already exists");
        }
    }

    /** Mot de passe refusé par la politique (400) ; la liste des règles non respectées est renvoyée. */
    public static final class WeakPassword extends RuntimeException {
        private final java.util.List<String> violations;

        public WeakPassword(java.util.List<String> violations) {
            super("weak password");
            this.violations = java.util.List.copyOf(violations);
        }

        public java.util.List<String> violations() {
            return violations;
        }
    }

    /** Le numéro ne correspond pas au pays choisi (400). */
    public static final class PhoneCountryMismatch extends RuntimeException {
        public PhoneCountryMismatch() {
            super("phone does not match country");
        }
    }

    /** Date de naissance hors limites : dans le futur ou avant 1900 (400). */
    public static final class InvalidBirthDate extends RuntimeException {
        public InvalidBirthDate() {
            super("invalid birth date");
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
