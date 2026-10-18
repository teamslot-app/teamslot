package ma.teamslot.venue.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "slot")
public class SlotEntity {

  @Id private UUID id;
  private UUID venueId;
  private Instant startAt;
  private Instant endAt;

  protected SlotEntity() {}

  public SlotEntity(UUID id, UUID venueId, Instant startAt, Instant endAt) {
    this.id = id;
    this.venueId = venueId;
    this.startAt = startAt;
    this.endAt = endAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getVenueId() {
    return venueId;
  }

  public Instant getStartAt() {
    return startAt;
  }

  public Instant getEndAt() {
    return endAt;
  }
}
