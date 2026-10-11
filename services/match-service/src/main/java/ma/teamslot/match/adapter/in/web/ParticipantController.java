package ma.teamslot.match.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import ma.teamslot.match.application.AddParticipantService;
import ma.teamslot.match.domain.Participant;
import ma.teamslot.match.domain.ParticipantStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** POST /api/v1/matches/{id}/participants (docs/api/match.yaml, addParticipant). */
@RestController
@RequestMapping("/api/v1/matches/{matchId}/participants")
public class ParticipantController {

    private final AddParticipantService service;

    public ParticipantController(AddParticipantService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ParticipantResponse> add(@PathVariable UUID matchId,
                                                   @Valid @RequestBody AddParticipantRequest request,
                                                   HttpServletRequest http) {
        UUID appelant = BearerCaller.subject(BearerCaller.token(http));
        Participant p = service.add(appelant, matchId, request.userId(), request.guestName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ParticipantResponse.from(p));
    }

    /** Exactement un des deux champs ; aucun champ pour un numero ou un e-mail. */
    public record AddParticipantRequest(UUID userId, @Size(max = Participant.SURNOM_MAX) String guestName) {
    }

    /** side et teamId sont prevus par le contrat pour la repartition des equipes (plus tard). */
    public record ParticipantResponse(UUID id, UUID matchId, UUID userId, String guestName,
                                      String side, UUID teamId, ParticipantStatus status) {
        static ParticipantResponse from(Participant p) {
            return new ParticipantResponse(p.id(), p.matchId(), p.userId(), p.guestName(),
                    null, null, p.status());
        }
    }
}
