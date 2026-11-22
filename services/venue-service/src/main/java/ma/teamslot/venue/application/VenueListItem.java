package ma.teamslot.venue.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;
import ma.teamslot.venue.domain.Money;

/** Terrain dans la liste (SCRUM-138). distanceMeters n'est présent qu'avec une position. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VenueListItem(
    UUID id,
    String name,
    double latitude,
    double longitude,
    Money pricePerHour,
    boolean acceptsOnSitePayment,
    List<EquipmentView> equipment,
    Integer distanceMeters) {}
