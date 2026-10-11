package ma.teamslot.match.application;

import ma.teamslot.match.domain.Participant;

/** Un participant et son côté (A, B ou null tant qu'il n'y a pas eu de tirage). */
public record ParticipantAvecCote(Participant participant, String side) {
}
