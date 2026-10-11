package ma.teamslot.venue.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentRepository extends JpaRepository<EquipmentEntity, UUID> {

  List<EquipmentEntity> findByVenueIdAndIdIn(UUID venueId, Collection<UUID> ids);
}
