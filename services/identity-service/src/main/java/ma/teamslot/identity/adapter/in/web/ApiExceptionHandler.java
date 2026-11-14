package ma.teamslot.identity.adapter.in.web;

import java.net.URI;

import ma.teamslot.identity.application.AuthErrors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Toutes les erreurs au format Problem Details (RFC 9457), comme le demandent les conventions.
 * ResponseEntityExceptionHandler traite déjà les erreurs de validation et le JSON mal formé (400).
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(AuthErrors.AccountAlreadyExists.class)
    ProblemDetail accountAlreadyExists() {
        // ne dit pas si c'est le numéro ou l'e-mail : on ne révèle pas quels comptes existent
        return problem(HttpStatus.CONFLICT, "account-exists", "Compte déjà existant",
                "Un compte existe déjà avec ce numéro ou cette adresse e-mail.");
    }

    @ExceptionHandler(AuthErrors.EmailNotVerified.class)
    ProblemDetail emailNotVerified() {
        return problem(HttpStatus.FORBIDDEN, "email-not-verified", "E-mail non vérifié",
                "Saisis le code reçu par e-mail pour activer ton compte.");
    }

    @ExceptionHandler(AuthErrors.InvalidCode.class)
    ProblemDetail invalidCode() {
        return problem(HttpStatus.BAD_REQUEST, "invalid-code", "Code incorrect",
                "Ce code n'est pas valide. Vérifie-le ou demande un nouveau code.");
    }

    @ExceptionHandler(AuthErrors.CodeExpired.class)
    ProblemDetail codeExpired() {
        return problem(HttpStatus.GONE, "code-expired", "Code expiré",
                "Ce code a expiré. Demande un nouveau code.");
    }

    @ExceptionHandler(AuthErrors.TooManyAttempts.class)
    ProblemDetail tooManyAttempts() {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "too-many-attempts", "Trop d'essais",
                "Trop d'essais pour ce code. Demande un nouveau code.");
    }

    @ExceptionHandler(AuthErrors.WeakPassword.class)
    ProblemDetail weakPassword(AuthErrors.WeakPassword e) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "weak-password", "Mot de passe trop faible",
                "Le mot de passe doit respecter : " + String.join(", ", e.violations()) + ".");
        problem.setProperty("violations", e.violations());
        return problem;
    }

    @ExceptionHandler(AuthErrors.PhoneCountryMismatch.class)
    ProblemDetail phoneCountryMismatch() {
        return problem(HttpStatus.BAD_REQUEST, "invalid-phone", "Numéro invalide",
                "Le numéro ne correspond pas au pays choisi.");
    }

    @ExceptionHandler(AuthErrors.InvalidBirthDate.class)
    ProblemDetail invalidBirthDate() {
        return problem(HttpStatus.BAD_REQUEST, "invalid-birth-date", "Date de naissance invalide",
                "La date de naissance doit être passée et postérieure au 1er janvier 1900.");
    }

    @ExceptionHandler(AuthErrors.InvalidCredentials.class)
    ProblemDetail invalidCredentials() {
        // même message que le téléphone soit inconnu ou le mot de passe faux
        return problem(HttpStatus.UNAUTHORIZED, "invalid-credentials", "Identifiants incorrects",
                "Téléphone ou mot de passe incorrect.");
    }

    @ExceptionHandler(AuthErrors.InvalidRefreshToken.class)
    ProblemDetail invalidRefreshToken() {
        return problem(HttpStatus.UNAUTHORIZED, "invalid-refresh-token", "Session expirée",
                "Le jeton de renouvellement n'est plus valide. Reconnectez-vous.");
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://teamslot.app/problems/" + type));
        problem.setTitle(title);
        return problem;
    }
}
