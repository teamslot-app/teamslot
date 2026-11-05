package ma.teamslot.payment.application;

import java.util.Optional;
import java.util.UUID;

/** Port de sortie : gérant de chaque terrain, appris de venue.created (SCRUM-75). */
public interface VenueOwnerRepository {

    /** Enregistre le gérant du terrain s'il n'est pas déjà connu (événement rejoué : sans effet). */
    void enregistrerSiAbsent(UUID venueId, UUID ownerId);

    Optional<UUID> gerant(UUID venueId);
}
