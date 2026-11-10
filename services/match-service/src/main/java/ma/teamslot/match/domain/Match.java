package ma.teamslot.match.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Un match de groupe. Les montants sont en centimes (docs/events.md).
 * maxPlayers compte l'organisateur ; venue n'a pas de capacité, d'où la valeur par défaut (SCRUM-78).
 */
public record Match(
        UUID id, String kind, String status, String visibility, int openSpots, int maxPlayers,
        UUID ownerId, UUID payerId, UUID venueId, UUID slotId, Instant startsAt,
        long totalAmountCents, String currency, boolean acceptsOnSitePayment, long version) {

    /** Foot à 5 : 10 joueurs. Aussi la valeur des matchs créés avant SCRUM-78 (champ absent). */
    public static final int MAX_JOUEURS_PAR_DEFAUT = 10;
}
