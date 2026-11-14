package ma.teamslot.notification.domain;

/** Types de notification, mêmes valeurs que NotificationType dans docs/api/notification.yaml (SCRUM-71). */
public enum TypeNotification {
    /** match.participant_added : on m'a ajouté à un match. */
    MATCH_PARTICIPANT_ADDED,
    /** match.join_accepted : ma demande de place est acceptée. */
    MATCH_JOIN_ACCEPTED,
    /** match.confirmed : un match dont je fais partie est confirmé. */
    MATCH_CONFIRMED
}
