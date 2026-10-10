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

    @ExceptionHandler(AuthErrors.PhoneAlreadyUsed.class)
    ProblemDetail phoneAlreadyUsed() {
        return problem(HttpStatus.CONFLICT, "phone-already-used", "Numéro déjà utilisé",
                "Ce numéro de téléphone est déjà associé à un compte.");
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
