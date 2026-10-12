package ma.teamslot.venue.adapter.in.web;

import java.net.URI;
import ma.teamslot.venue.application.NotFoundException;
import ma.teamslot.venue.application.SlotAlreadyReservedException;
import ma.teamslot.venue.application.SlotAlreadyStartedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Erreurs au format Problem Details (RFC 9457), même style que identity-service.
 * La classe parente renvoie déjà 400 pour une date mal formée, un corps invalide
 * ou un identifiant qui n'est pas un UUID.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(NotFoundException.class)
  ProblemDetail notFound(NotFoundException ex) {
    return problem(HttpStatus.NOT_FOUND, "not-found", "Ressource introuvable", ex.getMessage());
  }

  @ExceptionHandler(SlotAlreadyReservedException.class)
  ProblemDetail slotAlreadyReserved(SlotAlreadyReservedException ex) {
    return problem(
        HttpStatus.CONFLICT, "slot-already-reserved", "Créneau déjà réservé",
        "Ce créneau est déjà réservé.");
  }

  @ExceptionHandler(SlotAlreadyStartedException.class)
  ProblemDetail slotAlreadyStarted(SlotAlreadyStartedException ex) {
    return problem(
        HttpStatus.CONFLICT, "slot-already-started", "Créneau déjà commencé",
        "Ce créneau a déjà commencé.");
  }

  private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create("https://teamslot.app/problems/" + type));
    problem.setTitle(title);
    return problem;
  }
}
