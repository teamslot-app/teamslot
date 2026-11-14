package ma.teamslot.notification.adapter.in.web;

import java.net.URI;
import ma.teamslot.notification.application.NotificationIntrouvableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Erreurs au format Problem Details (RFC 9457), même style que payment-service et match-service. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotificationController.AppelantNonAuthentifieException.class)
    ProblemDetail nonAuthentifie(NotificationController.AppelantNonAuthentifieException ex) {
        return probleme(HttpStatus.UNAUTHORIZED, "unauthenticated", "Authentification requise",
                "Authentification requise.");
    }

    @ExceptionHandler(NotificationIntrouvableException.class)
    ProblemDetail introuvable(NotificationIntrouvableException ex) {
        return probleme(HttpStatus.NOT_FOUND, "notification-not-found", "Notification introuvable",
                "Aucune notification avec cet identifiant pour cet utilisateur.");
    }

    @ExceptionHandler(NotificationController.LimiteInvalideException.class)
    ProblemDetail limiteInvalide(NotificationController.LimiteInvalideException ex) {
        return probleme(HttpStatus.BAD_REQUEST, "invalid-limit", "Paramètre limit invalide",
                "limit doit être compris entre 1 et " + NotificationController.LIMITE_MAX + ".");
    }

    @ExceptionHandler(Curseur.CurseurInvalideException.class)
    ProblemDetail curseurInvalide(Curseur.CurseurInvalideException ex) {
        return probleme(HttpStatus.BAD_REQUEST, "invalid-cursor", "Curseur invalide",
                "Le curseur doit être une valeur nextCursor renvoyée telle quelle.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) {
        log.error("Erreur inattendue", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
            "Une erreur interne est survenue.");
    }

    private static ProblemDetail probleme(HttpStatus status, String type, String titre, String detail) {
        ProblemDetail probleme = ProblemDetail.forStatusAndDetail(status, detail);
        probleme.setType(URI.create("https://teamslot.app/problems/" + type));
        probleme.setTitle(titre);
        return probleme;
    }
}
