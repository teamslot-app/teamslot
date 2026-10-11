package ma.teamslot.venue.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SlotRepository extends JpaRepository<SlotEntity, UUID> {

  @Query(
      "select s from SlotEntity s where s.venueId = :venueId"
          + " and s.startAt >= :periodStart and s.startAt < :periodEnd order by s.startAt")
  List<SlotEntity> findOfPeriod(
      @Param("venueId") UUID venueId,
      @Param("periodStart") Instant periodStart,
      @Param("periodEnd") Instant periodEnd);
}
