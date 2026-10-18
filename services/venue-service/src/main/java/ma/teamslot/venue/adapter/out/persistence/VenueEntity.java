package ma.teamslot.venue.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "venue")
public class VenueEntity {

  @Id private UUID id;
  private UUID ownerId;
  private String name;
  private double latitude;
  private double longitude;
  private long pricePerHourCents;
  private String currency;
  private boolean acceptsOnSitePayment;

  protected VenueEntity() {}

  /** Nouveau terrain : l'identifiant est généré ici, le propriétaire est le sub du jeton vérifié. */
  public VenueEntity(
      UUID ownerId,
      String name,
      double latitude,
      double longitude,
      long pricePerHourCents,
      String currency,
      boolean acceptsOnSitePayment) {
    this.id = UUID.randomUUID();
    this.ownerId = ownerId;
    this.name = name;
    this.latitude = latitude;
    this.longitude = longitude;
    this.pricePerHourCents = pricePerHourCents;
    this.currency = currency;
    this.acceptsOnSitePayment = acceptsOnSitePayment;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public String getName() {
    return name;
  }

  public double getLatitude() {
    return latitude;
  }

  public double getLongitude() {
    return longitude;
  }

  public long getPricePerHourCents() {
    return pricePerHourCents;
  }

  public String getCurrency() {
    return currency;
  }

  public boolean isAcceptsOnSitePayment() {
    return acceptsOnSitePayment;
  }
}
