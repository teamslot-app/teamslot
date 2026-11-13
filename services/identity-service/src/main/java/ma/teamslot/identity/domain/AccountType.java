package ma.teamslot.identity.domain;

/** SCRUM-136 — type de compte demandé à l'inscription (contrat identity.yaml, schéma AccountType). */
public enum AccountType {
    PLAYER,
    /** Ne donne PAS le rôle : crée seulement une demande en attente (ManagerStatus.PENDING). */
    VENUE_MANAGER
}
