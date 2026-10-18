package ma.teamslot.venue.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ma.teamslot.venue.adapter.out.persistence.SlotEntity;
import ma.teamslot.venue.adapter.out.persistence.SlotRepository;
import ma.teamslot.venue.adapter.out.persistence.VenueEntity;
import ma.teamslot.venue.adapter.out.persistence.VenueRepository;
import ma.teamslot.venue.application.CreateVenueCommand.EquipmentCommand;
import ma.teamslot.venue.domain.Money;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SCRUM-74 : un gérant publie son terrain et ses créneaux.
 * Le rôle et l'identité viennent du JWT vérifié (jamais de la requête) : callerId = sub, role = claim "role".
 */
@Service
public class VenueAdminService {

  static final String ROLE_MANAGER = "VENUE_MANAGER";
  private static final Duration SLOT_LENGTH = Duration.ofHours(1);

  private final VenueRepository venues;
  private final SlotRepository slots;
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public VenueAdminService(
      VenueRepository venues, SlotRepository slots, JdbcTemplate jdbc, Clock clock) {
    this.venues = venues;
    this.slots = slots;
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Transactional
  public VenueView createVenue(UUID callerId, String role, CreateVenueCommand command) {
    requireManager(role);
    for (EquipmentCommand equipment : command.equipment()) {
      if (!equipment.currency().equals(command.currency())) {
        throw new InvalidRequestException("La devise d'un équipement doit être celle du terrain.");
      }
    }
    VenueEntity venue =
        venues.saveAndFlush(
            new VenueEntity(
                callerId,
                command.name().strip(),
                command.latitude(),
                command.longitude(),
                command.pricePerHourCents(),
                command.currency(),
                command.acceptsOnSitePayment()));
    for (EquipmentCommand equipment : command.equipment()) {
      // requête paramétrée (S8)
      jdbc.update(
          "insert into equipment (id, venue_id, name, price_cents) values (?, ?, ?, ?)",
          UUID.randomUUID(),
          venue.getId(),
          equipment.name().strip(),
          equipment.priceCents());
    }
    // EVENT_VENUE_CREATED : l'appel outbox.record(...) est ajouté à l'étape suivante
    return new VenueView(
        venue.getId(),
        venue.getName(),
        venue.getLatitude(),
        venue.getLongitude(),
        new Money(venue.getPricePerHourCents(), venue.getCurrency()),
        venue.isAcceptsOnSitePayment());
  }

  @Transactional
  public List<SlotView> createSlots(UUID callerId, String role, UUID venueId, List<Instant> starts) {
    requireManager(role);
    VenueEntity venue =
        venues.findById(venueId).orElseThrow(() -> new NotFoundException("Terrain inconnu"));
    if (!venue.getOwnerId().equals(callerId)) {
      throw new ForbiddenException("Ce terrain ne vous appartient pas.");
    }
    Instant now = clock.instant();
    if (starts.stream().anyMatch(start -> !start.isAfter(now))) {
      throw new InvalidRequestException("Un créneau doit commencer dans le futur.");
    }
    List<SlotEntity> created =
        starts.stream()
            .map(start -> new SlotEntity(UUID.randomUUID(), venueId, start, start.plus(SLOT_LENGTH)))
            .toList();
    try {
      // la contrainte EXCLUDE de la base refuse tout chevauchement, y compris dans la même demande
      slots.saveAllAndFlush(created);
    } catch (DataIntegrityViolationException e) {
      throw new SlotOverlapException("Un des créneaux chevauche un créneau existant.");
    }
    Money price = new Money(venue.getPricePerHourCents(), venue.getCurrency());
    return created.stream()
        .map(slot -> new SlotView(slot.getId(), slot.getStartAt(), slot.getEndAt(), true, price))
        .toList();
  }

  private static void requireManager(String role) {
    if (!ROLE_MANAGER.equals(role)) {
      throw new ForbiddenException("Réservé aux gérants de terrain.");
    }
  }
}
