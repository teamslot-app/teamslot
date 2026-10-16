package ma.teamslot.venue.adapter.in.kafka;

import java.util.Objects;
import java.util.UUID;

/** Champ utile de match.cancelled (docs/events.md). matchId manquant = message invalide (direct au .dlt). */
public record MatchCancelledData(UUID matchId) {

  public MatchCancelledData {
    Objects.requireNonNull(matchId, "matchId");
  }
}
