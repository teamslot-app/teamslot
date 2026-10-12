package ma.teamslot.venue.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import ma.teamslot.venue.application.ReservationService;
import ma.teamslot.venue.application.ReservationView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint INTERNE, appelé par match-service. Aucune route de la passerelle ne doit y mener.
 * Le prix n'est jamais lu dans la requête : il vient toujours de la base.
 */
@RestController
@RequestMapping("/api/v1/slot-reservations")
public class SlotReservationController {

  private final ReservationService reservations;

  public SlotReservationController(ReservationService reservations) {
    this.reservations = reservations;
  }

  @PostMapping
  public ResponseEntity<ReservationView> reserve(@Valid @RequestBody ReserveSlotRequest request) {
    ReservationView view =
        reservations.reserve(request.slotId(), request.matchId(), request.equipmentIds());
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  public record ReserveSlotRequest(
      @NotNull UUID slotId, @NotNull UUID matchId, List<UUID> equipmentIds) {}
}
