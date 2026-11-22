package ma.teamslot.venue.application;

import java.time.Instant;
import java.util.UUID;

/** Schéma VenueReservation du contrat venue.yaml : aucune donnée personnelle de joueur. */
public record VenueReservationView(
    UUID slotId, UUID matchId, Instant startsAt, Instant endsAt, String status) {}
