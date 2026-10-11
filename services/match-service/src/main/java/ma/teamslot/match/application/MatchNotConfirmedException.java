package ma.teamslot.match.application;

/** SCRUM-61 : seul un match CONFIRMED (payé en ligne ou accepté sur place) peut être réparti (409). */
public class MatchNotConfirmedException extends RuntimeException {
}
