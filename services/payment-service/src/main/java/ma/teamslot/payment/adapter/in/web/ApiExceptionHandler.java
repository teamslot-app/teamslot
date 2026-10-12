package ma.teamslot.payment.adapter.in.web;

import java.net.URI;
import ma.teamslot.payment.application.CallerNotAuthenticatedException;
import ma.teamslot.payment.application.PaymentNotFoundException;
import ma.teamslot.payment.domain.NotPayerException;
import ma.teamslot.payment.domain.OnSitePaymentNotAcceptedException;
import ma.teamslot.payment.domain.PaymentNotPendingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Erreurs au format Problem Details (RFC 9457), meme style que match-service et identity-service. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(CallerNotAuthenticatedException.class)
    ProblemDetail nonAuthentifie(CallerNotAuthenticatedException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "unauthenticated", "Authentification requise",
                "Authentification requise.");
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    ProblemDetail introuvable(PaymentNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "payment-not-found", "Paiement introuvable",
                "Aucun paiement pour ce match.");
    }

    @ExceptionHandler(NotPayerException.class)
    ProblemDetail pasPayeur(NotPayerException ex) {
        return problem(HttpStatus.FORBIDDEN, "not-payer", "Action réservée au responsable du paiement",
                "Seul le responsable du paiement peut faire cette action.");
    }

    @ExceptionHandler(PaymentNotPendingException.class)
    ProblemDetail plusEnAttente(PaymentNotPendingException ex) {
        return problem(HttpStatus.CONFLICT, "payment-not-pending", "Paiement déjà traité",
                "Ce paiement a déjà été effectué ou son mode a déjà été choisi.");
    }

    @ExceptionHandler(OnSitePaymentNotAcceptedException.class)
    ProblemDetail surPlaceRefuse(OnSitePaymentNotAcceptedException ex) {
        return problem(HttpStatus.CONFLICT, "on-site-payment-not-accepted", "Paiement sur place refusé",
                "Ce terrain n'accepte pas le paiement sur place.");
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
