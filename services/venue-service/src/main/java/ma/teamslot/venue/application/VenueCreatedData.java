package ma.teamslot.venue.application;

import java.util.UUID;
import ma.teamslot.venue.domain.Money;

/** Champ data de venue.created : les champs du catalogue docs/events.md. */
public record VenueCreatedData(
    UUID venueId,
    UUID ownerId,
    String name,
    double latitude,
    double longitude,
    Money pricePerHour,
    boolean acceptsOnSitePayment) {}
