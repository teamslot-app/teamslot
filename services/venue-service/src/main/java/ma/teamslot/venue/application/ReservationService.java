package ma.teamslot.venue.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import ma.teamslot.venue.adapter.out.persistence.EquipmentEntity;
import ma.teamslot.venue.adapter.out.persistence.EquipmentRepository;
import ma.teamslot.venue.adapter.out.persistence.SlotEntity;
import ma.teamslot.venue.adapter.out.persistence.SlotRepository;
import ma.teamslot.venue.adapter.out.persistence.VenueEntity;
import ma.teamslot.venue.adapter.out.persistence.VenueRepository;
import ma.teamslot.venue.application.ReservationView.EquipmentView;
import ma.teamslot.venue.domain.Money;
import ma.teamslot.venue.domain.PriceCalculator;
import ma.teamslot.events.Outbox;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

  private static final String RESERVED = "RESERVED";

  private static final String INSERT_RESERVATION =
      """
      INSERT INTO slot_reservation (id, slot_id, match_id, status, total_amount_cents, currency)
      VALUES (:id, :slotId, :matchId, 'RESERVED', :total, :currency)
      """;

  private static final String INSERT_EQUIPMENT =
      """
      INSERT INTO slot_reservation_equipment (reservation_id, equipment_id)
      VALUES (:reservationId, :equipmentId)
      """;

  private final SlotRepository slots;
  private final VenueRepository venues;
  private final EquipmentRepository equipment;
  private final JdbcClient jdbc;
  private final Outbox outbox;
  private final Clock clock;
  private final PriceCalculator priceCalculator = new PriceCalculator();

  public ReservationService(
      SlotRepository slots,
      VenueRepository venues,
      EquipmentRepository equipment,
      JdbcClient jdbc,
      Outbox outbox,
      Clock clock) {
    this.slots = slots;
    this.venues = venues;
    this.equipment = equipment;
    this.jdbc = jdbc;
    this.outbox = outbox;
    this.clock = clock;
  }

  /**
   * Pas de « le créneau est-il libre ? » avant d'insérer : deux requêtes simultanées passeraient
   * toutes les deux. C'est l'index unique partiel sur les réservations RESERVED qui garantit
   * l'unicité : la seconde insertion échoue, et on la traduit en 409.
   */
  @Transactional
  public ReservationView reserve(UUID slotId, UUID matchId, List<UUID> equipmentIds) {
    SlotEntity slot =
        slots.findById(slotId).orElseThrow(() -> new NotFoundException("Créneau inconnu"));
    if (!slot.getStartAt().isAfter(clock.instant())) {
      throw new SlotAlreadyStartedException(slotId);
    }
    VenueEntity venue =
        venues
            .findById(slot.getVenueId())
            .orElseThrow(() -> new NotFoundException("Terrain inconnu"));

    List<UUID> ids = equipmentIds == null ? List.of() : equipmentIds.stream().distinct().toList();
    List<EquipmentEntity> chosen =
        ids.isEmpty() ? List.of() : equipment.findByVenueIdAndIdIn(venue.getId(), ids);
    if (chosen.size() != ids.size()) {
      throw new NotFoundException("Équipement inconnu pour ce terrain");
    }

    Money base = new Money(venue.getPricePerHourCents(), venue.getCurrency());
    List<Money> supplements =
        chosen.stream().map(e -> new Money(e.getPriceCents(), venue.getCurrency())).toList();
    Money total = priceCalculator.total(base, supplements);

    UUID reservationId = UUID.randomUUID();
    try {
      jdbc.sql(INSERT_RESERVATION)
          .param("id", reservationId)
          .param("slotId", slotId)
          .param("matchId", matchId)
          .param("total", total.amount())
          .param("currency", total.currency())
          .update();
    } catch (DuplicateKeyException e) {
      throw new SlotAlreadyReservedException(slotId);
    }
    for (EquipmentEntity item : chosen) {
      jdbc.sql(INSERT_EQUIPMENT)
          .param("reservationId", reservationId)
          .param("equipmentId", item.getId())
          .update();
    }

    outbox.record(
        venue.getId(),
        "slot.reserved",
        new SlotReservedData(
            venue.getId(),
            slotId,
            matchId,
            slot.getStartAt(),
            slot.getEndAt(),
            total.amount(),
            total.currency()));

    List<EquipmentView> equipmentViews =
        chosen.stream()
            .map(
                e ->
                    new EquipmentView(
                        e.getId(), e.getName(), new Money(e.getPriceCents(), venue.getCurrency())))
            .toList();
    return new ReservationView(
        slotId,
        matchId,
        venue.getId(),
        equipmentViews,
        total,
        venue.isAcceptsOnSitePayment(),
        RESERVED,
        slot.getStartAt(),
        slot.getEndAt());
  }
}
