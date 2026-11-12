package ma.teamslot.identity.domain;

/** SCRUM-136 — état de la demande « gérant de terrain » d'un compte (contrat identity.yaml, schéma ManagerStatus). */
public enum ManagerStatus {
    /** Aucune demande : compte joueur. */
    NONE,
    /** Demande faite à l'inscription, en attente d'un administrateur ; le compte reste PLAYER. */
    PENDING,
    /** Validée par un administrateur : platformRole = VENUE_MANAGER. */
    APPROVED,
    /** Refusée : le compte reste joueur. */
    REJECTED
}
