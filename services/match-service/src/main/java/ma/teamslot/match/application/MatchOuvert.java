package ma.teamslot.match.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Résumé public d'un match aux places ouvertes (SCRUM-118). Volontairement sans organisateur
 * ni participant : n'importe quel joueur connecté peut le lire.
 */
public record MatchOuvert(UUID id, UUID venueId, Instant startsAt, int openSpots, int maxPlayers) {
}
