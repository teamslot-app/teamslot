package ma.teamslot.match.domain;

/** Statuts d'un participant (docs/api/match.yaml, ParticipantStatus). */
public enum ParticipantStatus {
    /** Compte ajoute par l'organisateur, en attente de sa confirmation. */
    INVITED,
    /** Participation confirmee (invite sans compte : l'organisateur repond de lui). */
    CONFIRMED,
    /** Demande pour rejoindre un match ouvert (hors SCRUM-60). */
    REQUESTED,
    /** Invitation refusee (hors SCRUM-60). */
    DECLINED
}
