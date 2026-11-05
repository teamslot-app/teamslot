package ma.teamslot.venue.application;

import java.util.List;

public record CreateVenueCommand(
    String name,
    double latitude,
    double longitude,
    long pricePerHourCents,
    String currency,
    boolean acceptsOnSitePayment,
    List<EquipmentCommand> equipment) {

  public record EquipmentCommand(String name, long priceCents, String currency) {}
}
