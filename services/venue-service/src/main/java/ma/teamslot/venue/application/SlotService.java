package ma.teamslot.venue.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import ma.teamslot.venue.adapter.out.persistence.EquipmentEntity;
import ma.teamslot.venue.adapter.out.persistence.EquipmentRepository;
import ma.teamslot.venue.adapter.out.persistence.SlotEntity;
import ma.teamslot.venue.adapter.out.persistence.SlotRepository;
import ma.teamslot.venue.adapter.out.persistence.SlotReservationRepository;
import ma.teamslot.venue.adapter.out.persistence.VenueEntity;
import ma.teamslot.venue.adapter.out.persistence.VenueRepository;
import ma.teamslot.venue.domain.Money;
import ma.teamslot.venue.domain.PriceCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SlotService {

  /** La date demandée est une journée locale du terrain. */
  static final ZoneId VENUE_ZONE = ZoneId.of("Africa/Casablanca");

  private final VenueRepository venues;
  private final SlotRepository slots;
  private final SlotReservationRepository reservations;
  private final EquipmentRepository equipment;
  private final Clock clock;
  private final PriceCalculator priceCalculator = new PriceCalculator();

  public SlotService(
      VenueRepository venues,
      SlotRepository slots,
      SlotReservationRepository reservations,
      EquipmentRepository equipment,
      Clock clock) {
    this.venues = venues;
    this.slots = slots;
    this.reservations = reservations;
    this.equipment = equipment;
    this.clock = clock;
  }

  public List<SlotView> slotsOfDay(UUID venueId, LocalDate date) {
    VenueEntity venue = findVenue(venueId);
    Instant from = date.atStartOfDay(VENUE_ZONE).toInstant();
    Instant to = date.plusDays(1).atStartOfDay(VENUE_ZONE).toInstant();

    List<SlotEntity> found = slots.findOfPeriod(venueId, from, to);
    if (found.isEmpty()) {
      return List.of();
    }
    Set<UUID> taken =
        Set.copyOf(
            reservations.findActiveSlotIds(found.stream().map(SlotEntity::getId).toList()));
    Money price = new Money(venue.getPricePerHourCents(), venue.getCurrency());
    Instant now = clock.instant();

    return found.stream()
        .map(
            slot ->
                new SlotView(
                    slot.getId(),
                    slot.getStartAt(),
                    slot.getEndAt(),
                    !taken.contains(slot.getId()) && slot.getStartAt().isAfter(now),
                    price))
        .toList();
  }

  public PriceView price(UUID venueId, UUID slotId, List<UUID> equipmentIds) {
    VenueEntity venue = findVenue(venueId);
    SlotEntity slot =
        slots
            .findById(slotId)
            .filter(s -> s.getVenueId().equals(venueId))
            .orElseThrow(() -> new NotFoundException("Créneau inconnu pour ce terrain"));

    List<UUID> ids = equipmentIds == null ? List.of() : equipmentIds.stream().distinct().toList();
    List<EquipmentEntity> chosen =
        ids.isEmpty() ? List.of() : equipment.findByVenueIdAndIdIn(venueId, ids);
    if (chosen.size() != ids.size()) {
      throw new NotFoundException("Équipement inconnu pour ce terrain");
    }

    Money base = new Money(venue.getPricePerHourCents(), venue.getCurrency());
    List<Money> supplements =
        chosen.stream().map(e -> new Money(e.getPriceCents(), venue.getCurrency())).toList();
    PriceCalculator.Breakdown breakdown = priceCalculator.breakdown(base, supplements);

    return new PriceView(
        slot.getId(), breakdown.base(), breakdown.equipmentTotal(), breakdown.total());
  }

  private VenueEntity findVenue(UUID venueId) {
    return venues.findById(venueId).orElseThrow(() -> new NotFoundException("Terrain inconnu"));
  }
}
