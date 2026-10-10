package ma.teamslot.match.adapter.out.venue;

import java.util.List;
import java.util.UUID;
import ma.teamslot.match.application.VenueClient;
import ma.teamslot.match.application.VenueUnavailableException;
import org.springframework.stereotype.Component;

/** Provisoire : venue-service n'est pas encore branché. À remplacer par VenueHttpClient. */
@Component
public class UnavailableVenueClient implements VenueClient {

    @Override
    public VenueReservation reserve(UUID matchId, UUID slotId, List<UUID> equipmentIds) {
        throw new VenueUnavailableException("venue-service n'est pas encore branché");
    }
}
