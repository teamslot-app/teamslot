package ma.teamslot.match.application;

/** Jeton absent, illisible, ou refusé par venue-service : 401. */
public class CallerNotAuthenticatedException extends RuntimeException {
    public CallerNotAuthenticatedException() {
        super("Appelant non authentifié");
    }
}
