package ma.teamslot.venue.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "venue")
public class VenueEntity {

  @Id private UUID id;
  private long pricePerHourCents;
  private String currency;
  private boolean acceptsOnSitePayment;

  protected VenueEntity() {}

  public UUID getId() {
    return id;
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
