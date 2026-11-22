package ma.teamslot.venue.adapter.in.web;

import ma.teamslot.venue.application.VenueListPage;
import ma.teamslot.venue.application.VenueListService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** SCRUM-138 : liste des terrains publiés (contrat docs/api/venue.yaml, listVenues). */
@RestController
@RequestMapping("/api/v1/venues")
public class VenueListController {

  private final VenueListService service;

  public VenueListController(VenueListService service) {
    this.service = service;
  }

  @GetMapping
  public VenueListPage list(
      @RequestParam(required = false) Double lat,
      @RequestParam(required = false) Double lon,
      @RequestParam(defaultValue = "5000") int radius,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(lat, lon, radius, page, size);
  }
}
