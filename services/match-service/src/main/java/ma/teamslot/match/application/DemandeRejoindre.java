package ma.teamslot.match.application;

import java.time.Instant;
import java.util.UUID;

/** SCRUM-79 : demande d'un joueur avec compte pour rejoindre un match aux places ouvertes. */
public record DemandeRejoindre(UUID matchId, UUID userId, Instant requestedAt) {
}
