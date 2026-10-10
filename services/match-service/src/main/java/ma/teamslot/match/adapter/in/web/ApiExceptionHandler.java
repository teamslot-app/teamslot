package ma.teamslot.match.adapter.in.web;

import java.net.URI;
import ma.teamslot.match.application.SlotAlreadyReservedException;
import ma.teamslot.match.application.SlotNotFoundException;
import ma.teamslot.match.application.VenueUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Erreurs au format Problem Details (RFC 9457), même style que identity-service. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MissingCallerException.class)
    ProblemDetail nonAuthentifie(MissingCallerException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "unauthenticated", "Authentification requise",
                "Authentification requise.");
    }

    @ExceptionHandler(SlotNotFoundException.class)
    ProblemDetail creneauIntrouvable(SlotNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "slot-not-found", "Créneau introuvable",
                "Ce créneau n'existe pas.");
    }

    @ExceptionHandler(SlotAlreadyReservedException.class)
    ProblemDetail creneauPris(SlotAlreadyReservedException ex) {
        return problem(HttpStatus.CONFLICT, "slot-already-reserved", "Créneau déjà réservé",
                "Ce créneau est déjà réservé.");
    }

    @ExceptionHandler(VenueUnavailableException.class)
    ProblemDetail venueIndisponible(VenueUnavailableException ex) {
        log.warn("venue-service indisponible : {}", ex.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "venue-unavailable", "Service indisponible",
                "Le service des terrains est momentanément indisponible.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) {
        log.error("Erreur inattendue", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
            "Une erreur interne est survenue.");
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://teamslot.app/problems/" + type));
        problem.setTitle(title);
        return problem;
    }
}
