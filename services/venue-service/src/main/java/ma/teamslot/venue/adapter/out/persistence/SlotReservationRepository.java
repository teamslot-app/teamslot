package ma.teamslot.venue.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SlotReservationRepository extends JpaRepository<SlotReservationEntity, UUID> {

  @Query(
      "select r.slotId from SlotReservationEntity r"
          + " where r.slotId in :slotIds and r.status = 'RESERVED'")
  List<UUID> findActiveSlotIds(@Param("slotIds") Collection<UUID> slotIds);
}
