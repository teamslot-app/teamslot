package ma.teamslot.venue.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Lecture seule pour SCRUM-58 ; SCRUM-93 ajoutera les colonnes d'écriture. */
@Entity
@Table(name = "slot_reservation")
public class SlotReservationEntity {

  @Id private UUID id;
  private UUID slotId;
  private String status;

  protected SlotReservationEntity() {}

  public UUID getId() {
    return id;
  }

  public UUID getSlotId() {
    return slotId;
  }

  public String getStatus() {
    return status;
  }
}
