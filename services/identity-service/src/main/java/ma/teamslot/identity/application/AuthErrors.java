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

    /** Mot de passe correct, mais e-mail pas encore vérifié (403) : le front propose de saisir le code. */
    public static final class EmailNotVerified extends RuntimeException {
        public EmailNotVerified() {
            super("email not verified");
        }
    }

    /** Code faux, déjà utilisé, ou e-mail inconnu (400) : la réponse ne dit pas lequel. */
    public static final class InvalidCode extends RuntimeException {
        public InvalidCode() {
            super("invalid code");
        }
    }

    /** Code expiré (410) : il faut en demander un nouveau. */
    public static final class CodeExpired extends RuntimeException {
        public CodeExpired() {
            super("code expired");
        }
    }

    /** SCRUM-95 : jeton valide mais compte introuvable (supprimé depuis) : 401, il faut se reconnecter. */
    public static final class AccountNotFound extends RuntimeException {
        public AccountNotFound() {
            super("account not found");
        }
    }

    /** SCRUM-95 : modification du profil refusée (400). Le message ne contient jamais la valeur envoyée. */
    public static final class InvalidProfile extends RuntimeException {
        public InvalidProfile(String message) {
            super(message);
        }
    }

    /** SCRUM-82 : texte de recherche refusé (400) : 3 à 40 caractères. Le message ne contient jamais ce texte. */
    public static final class InvalidSearch extends RuntimeException {
        public InvalidSearch(String message) {
            super(message);
        }
    }

    /** SCRUM-82 : trop de recherches de joueurs pour ce compte (429) ; secondes à attendre (en-tête Retry-After). */
    public static final class TooManySearches extends RuntimeException {
        private final long retryAfterSeconds;

        public TooManySearches(long retryAfterSeconds) {
            super("too many searches");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    /** 5 essais déjà faits pour ce code (429) : il faut en demander un nouveau. */
    public static final class TooManyAttempts extends RuntimeException {
        public TooManyAttempts() {
            super("too many attempts");
        }
    }
}
