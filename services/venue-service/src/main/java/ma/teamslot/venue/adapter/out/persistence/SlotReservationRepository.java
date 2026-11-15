package ma.teamslot.venue.adapter.out.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import ma.teamslot.venue.application.VenueReservationView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SlotReservationRepository extends JpaRepository<SlotReservationEntity, UUID> {

  @Query(
      "select r.slotId from SlotReservationEntity r"
          + " where r.slotId in :slotIds and r.status = 'RESERVED'")
  List<UUID> findActiveSlotIds(@Param("slotIds") Collection<UUID> slotIds);

  /** SCRUM-139 : réservations actives d'un terrain sur une période (requête paramétrée, S8). */
  @Query(
      "select new ma.teamslot.venue.application.VenueReservationView("
          + "r.slotId, r.matchId, s.startAt, s.endAt, r.status)"
          + " from SlotReservationEntity r, SlotEntity s"
          + " where s.id = r.slotId and s.venueId = :venueId and r.status = 'RESERVED'"
          + " and s.startAt >= :periodStart and s.startAt < :periodEnd order by s.startAt")
  List<VenueReservationView> findOfVenueAndPeriod(
      @Param("venueId") UUID venueId,
      @Param("periodStart") Instant periodStart,
      @Param("periodEnd") Instant periodEnd);
}
