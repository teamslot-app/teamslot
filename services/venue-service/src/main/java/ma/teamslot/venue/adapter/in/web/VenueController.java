package ma.teamslot.venue.adapter.in.web;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.teamslot.venue.application.PriceView;
import ma.teamslot.venue.application.SlotService;
import ma.teamslot.venue.application.SlotView;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/venues/{venueId}")
public class VenueController {

  private final SlotService slotService;

  public VenueController(SlotService slotService) {
    this.slotService = slotService;
  }

  @GetMapping("/slots")
  public List<SlotView> listSlots(
      @PathVariable UUID venueId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return slotService.slotsOfDay(venueId, date);
  }

  @GetMapping("/price")
  public PriceView getPrice(
      @PathVariable UUID venueId,
      @RequestParam("slot") UUID slotId,
      @RequestParam(name = "equipment", required = false) List<UUID> equipment) {
    return slotService.price(venueId, slotId, equipment);
  }
}
