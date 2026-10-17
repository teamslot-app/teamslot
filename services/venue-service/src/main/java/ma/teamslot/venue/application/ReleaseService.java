package ma.teamslot.venue.application;

import java.util.List;
import java.util.UUID;
import ma.teamslot.events.Outbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * SCRUM-76 : un match annulé (match.cancelled) libère son créneau. La réservation passe de RESERVED à
 * RELEASED (vocabulaire du contrat, V3) et slot.released est publié. Le créneau redevient libre :
 * l'index unique ne porte que sur les réservations RESERVED.
 * A appeler dans la transaction du consommateur idempotent (outbox MANDATORY).
 */
@Service
public class ReleaseService {

  static final String SLOT_RELEASED = "slot.released";
  static final String RAISON = "MATCH_CANCELLED";
  private static final Logger LOG = LoggerFactory.getLogger(ReleaseService.class);

  /** Une seule requête : met à jour et renvoie ce qui a été libéré (rien si déjà RELEASED ou inconnu). */
  private static final String LIBERER =
      """
      UPDATE slot_reservation r SET status = 'RELEASED'
      FROM slot s
      WHERE r.match_id = :matchId AND r.status = 'RESERVED' AND s.id = r.slot_id
      RETURNING r.slot_id, s.venue_id
      """;

  private record Libere(UUID slotId, UUID venueId) {}

  private final JdbcClient jdbc;
  private final Outbox outbox;

  public ReleaseService(JdbcClient jdbc, Outbox outbox) {
    this.jdbc = jdbc;
    this.outbox = outbox;
  }

  public void surMatchAnnule(UUID matchId) {
    List<Libere> liberes =
        jdbc.sql(LIBERER)
            .param("matchId", matchId)
            .query(
                (rs, i) ->
                    new Libere(
                        rs.getObject("slot_id", UUID.class), rs.getObject("venue_id", UUID.class)))
            .list();
    if (liberes.isEmpty()) {
      LOG.info("match.cancelled pour le match {} : aucune réservation RESERVED, ignoré", matchId);
      return;
    }
    for (Libere libere : liberes) {
      outbox.record(
          libere.venueId(),
          SLOT_RELEASED,
          new SlotReleasedData(libere.venueId(), libere.slotId(), matchId, RAISON));
    }
  }
}
