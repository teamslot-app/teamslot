package ma.teamslot.venue.adapter.in.web;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import ma.teamslot.venue.application.ForbiddenException;
import ma.teamslot.venue.application.SlotView;
import ma.teamslot.venue.application.VenueAdminService;
import ma.teamslot.venue.application.VenueView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** SCRUM-74 : publication par le gérant. Identité et rôle viennent du JWT vérifié, jamais de la requête. */
@RestController
@RequestMapping("/api/v1/venues")
public class VenueAdminController {

  private final VenueAdminService service;

  public VenueAdminController(VenueAdminService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<VenueView> createVenue(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateVenueRequest request) {
    VenueView venue = service.createVenue(callerId(jwt), jwt.getClaimAsString("role"), request.toCommand());
    return ResponseEntity.created(URI.create("/api/v1/venues/" + venue.id())).body(venue);
  }

  @PostMapping("/{venueId}/slots")
  public ResponseEntity<List<SlotView>> createSlots(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID venueId,
      @Valid @RequestBody CreateSlotsRequest request) {
    List<SlotView> created =
        service.createSlots(callerId(jwt), jwt.getClaimAsString("role"), venueId, request.starts());
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  private static UUID callerId(Jwt jwt) {
    try {
      return UUID.fromString(jwt.getSubject());
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new ForbiddenException("Jeton sans identité valide.");
    }
  }
}
