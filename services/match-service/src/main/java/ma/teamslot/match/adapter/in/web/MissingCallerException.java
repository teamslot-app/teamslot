package ma.teamslot.match.adapter.in.web;

public class MissingCallerException extends RuntimeException {
    public MissingCallerException() {
        super("Appelant absent ou invalide");
    }
}
