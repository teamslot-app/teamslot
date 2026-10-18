package ma.teamslot.venue.application;

import java.util.UUID;
import ma.teamslot.venue.domain.Money;

public record VenueView(
    UUID id,
    String name,
    double latitude,
    double longitude,
    Money pricePerHour,
    boolean acceptsOnSitePayment) {}
