package ma.teamslot.match.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Port vers venue-service (POST /api/v1/slot-reservations, appel interne et synchrone).
 * Le jeton de l'utilisateur est transmis tel quel (Authorization: Bearer) : venue le vérifie.
 * Pas de méthode de libération : venue consomme match.cancelled (SCRUM-76).
 */
public interface VenueClient {

    /** @throws SlotAlreadyReservedException 409
     *  @throws SlotNotFoundException 404
     *  @throws CallerNotAuthenticatedException 401 (jeton refusé par venue)
     *  @throws VenueUnavailableException venue ne répond pas ou répond mal */
    VenueReservation reserve(UUID matchId, UUID slotId, List<UUID> equipmentIds, String bearerToken);

    /** Réponse de venue : le prix vient toujours d'ici, jamais du client. */
    record VenueReservation(UUID venueId, Instant startsAt, long totalAmountCents,
                            String currency, boolean acceptsOnSitePayment) {
    }
}
