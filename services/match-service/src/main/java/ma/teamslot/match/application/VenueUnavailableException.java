package ma.teamslot.match.application;

public class VenueUnavailableException extends RuntimeException {
    public VenueUnavailableException(String message) {
        super(message);
    }
}
