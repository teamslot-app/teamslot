package ma.teamslot.match.application;

import java.util.UUID;

/** Champ data de match.participant_added (docs/events.md) : userId OU guestName, l'autre est null. */
public record ParticipantAddedData(UUID matchId, UUID participantId, UUID userId, String guestName) {
}
