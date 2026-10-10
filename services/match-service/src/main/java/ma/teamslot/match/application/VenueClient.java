package ma.teamslot.match.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Port vers venue-service (POST /api/v1/slot-reservations, appel interne et synchrone).
 * Pas de méthode de libération : venue consomme match.cancelled (docs/events.md, point 2).
 */
public interface VenueClient {

    /** @throws SlotAlreadyReservedException si le créneau est pris (409)
     *  @throws SlotNotFoundException si le créneau n'existe pas (404)
     *  @throws VenueUnavailableException si venue ne répond pas */
    VenueReservation reserve(UUID matchId, UUID slotId, List<UUID> equipmentIds);

    /** Réponse de venue : le prix vient toujours d'ici, jamais du client. */
    record VenueReservation(UUID venueId, Instant startsAt, long totalAmountCents,
                            String currency, boolean acceptsOnSitePayment) {
    }
}
