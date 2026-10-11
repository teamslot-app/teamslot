package ma.teamslot.venue.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "equipment")
public class EquipmentEntity {

  @Id private UUID id;
  private UUID venueId;
  private String name;
  private long priceCents;

  protected EquipmentEntity() {}

  public UUID getId() {
    return id;
  }

  public UUID getVenueId() {
    return venueId;
  }

  public String getName() {
    return name;
  }

  public long getPriceCents() {
    return priceCents;
  }
}
