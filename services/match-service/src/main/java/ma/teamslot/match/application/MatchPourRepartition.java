package ma.teamslot.match.application;

import java.util.UUID;

/** Ce qu'il faut savoir d'un match pour le répartir (SCRUM-61). */
public record MatchPourRepartition(UUID organisateur, String statut) {
}
