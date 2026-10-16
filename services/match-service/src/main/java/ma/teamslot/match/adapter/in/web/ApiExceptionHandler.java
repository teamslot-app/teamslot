package ma.teamslot.match.adapter.in.web;

import java.net.URI;
import ma.teamslot.match.application.CallerNotAuthenticatedException;
import ma.teamslot.match.application.MatchNotConfirmedException;
import ma.teamslot.match.application.NotAParticipantException;
import ma.teamslot.match.application.NotEnoughPlayersException;
import ma.teamslot.match.application.UnknownStrategyException;
import ma.teamslot.match.application.MatchClosedException;
import ma.teamslot.match.application.MatchNotCancellableException;
import ma.teamslot.match.application.MatchNotFoundException;
import ma.teamslot.match.application.NotMatchOrganizerException;
import ma.teamslot.match.application.ParticipantAlreadyAddedException;
import ma.teamslot.match.application.SlotAlreadyReservedException;
import ma.teamslot.match.application.SlotNotFoundException;
import ma.teamslot.match.application.VenueUnavailableException;
import ma.teamslot.match.domain.InvalidParticipantException;
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

    @ExceptionHandler(CallerNotAuthenticatedException.class)
    ProblemDetail nonAuthentifie(CallerNotAuthenticatedException ex) {
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

    @ExceptionHandler(MatchNotFoundException.class)
    ProblemDetail matchIntrouvable(MatchNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "match-not-found", "Match introuvable",
                "Ce match n'existe pas.");
    }

    @ExceptionHandler(NotMatchOrganizerException.class)
    ProblemDetail pasOrganisateur(NotMatchOrganizerException ex) {
        return problem(HttpStatus.FORBIDDEN, "not-match-organizer", "Action réservée à l'organisateur",
                "Cette action est réservée à l'organisateur du match.");
    }

    @ExceptionHandler(ParticipantAlreadyAddedException.class)
    ProblemDetail dejaParticipant(ParticipantAlreadyAddedException ex) {
        String detail = switch (ex.raison()) {
            case COMPTE -> "Ce compte participe déjà au match.";
            case SURNOM -> "Ce surnom est déjà utilisé dans ce match : choisis-en un autre (par exemple « Yassir 2 »).";
            case ORGANISATEUR -> "L'organisateur fait déjà partie du match.";
        };
        return problem(HttpStatus.CONFLICT, "participant-already-added", "Déjà participant", detail);
    }

    @ExceptionHandler(MatchNotCancellableException.class)
    ProblemDetail nonAnnulable(MatchNotCancellableException ex) {
        return problem(HttpStatus.CONFLICT, "match-not-cancellable", "Match non annulable",
                "Un match joué, terminé ou déjà annulé ne peut pas être annulé.");
    }

    @ExceptionHandler(MatchClosedException.class)
    ProblemDetail matchFerme(MatchClosedException ex) {
        return problem(HttpStatus.CONFLICT, "match-closed", "Match fermé",
                "Ce match n'accepte plus de participants.");
    }

    /** Message de la règle du domaine : il ne contient jamais la valeur envoyée. */
    @ExceptionHandler(InvalidParticipantException.class)
    ProblemDetail participantInvalide(InvalidParticipantException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-participant", "Participant invalide",
                ex.getMessage());
    }

    @ExceptionHandler(UnknownStrategyException.class)
    ProblemDetail strategieInconnue(UnknownStrategyException ex) {
        return problem(HttpStatus.BAD_REQUEST, "unknown-strategy", "Stratégie inconnue", ex.getMessage());
    }

    @ExceptionHandler(MatchNotConfirmedException.class)
    ProblemDetail matchPasConfirme(MatchNotConfirmedException ex) {
        return problem(HttpStatus.CONFLICT, "match-not-confirmed", "Match non confirmé",
                "Le match doit être confirmé (paiement effectué ou accepté sur place) avant la répartition.");
    }

    @ExceptionHandler(NotEnoughPlayersException.class)
    ProblemDetail pasAssezDeJoueurs(NotEnoughPlayersException ex) {
        return problem(HttpStatus.CONFLICT, "not-enough-players", "Pas assez de joueurs",
                "Il faut au moins deux joueurs pour répartir les équipes.");
    }

    @ExceptionHandler(NotAParticipantException.class)
    ProblemDetail pasParticipant(NotAParticipantException ex) {
        return problem(HttpStatus.FORBIDDEN, "not-a-participant", "Accès réservé aux participants",
                "Seuls l'organisateur et les participants du match peuvent voir cette liste.");
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
