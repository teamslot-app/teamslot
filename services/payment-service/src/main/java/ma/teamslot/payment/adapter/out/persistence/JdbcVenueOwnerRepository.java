package ma.teamslot.payment.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import ma.teamslot.payment.application.VenueOwnerRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcVenueOwnerRepository implements VenueOwnerRepository {

    private final JdbcClient jdbc;

    public JdbcVenueOwnerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void enregistrerSiAbsent(UUID venueId, UUID ownerId) {
        // Le gérant d'un terrain ne change pas : le premier venue.created fait foi.
        jdbc.sql("INSERT INTO venue_owners (venue_id, owner_id) VALUES (:venue, :owner) ON CONFLICT (venue_id) DO NOTHING")
                .param("venue", venueId).param("owner", ownerId).update();
    }

    @Override
    public Optional<UUID> gerant(UUID venueId) {
        return jdbc.sql("SELECT owner_id FROM venue_owners WHERE venue_id = :venue")
                .param("venue", venueId).query(UUID.class).optional();
    }
}
