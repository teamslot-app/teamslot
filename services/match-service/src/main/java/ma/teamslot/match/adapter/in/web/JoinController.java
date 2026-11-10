package ma.teamslot.match.adapter.in.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ma.teamslot.match.adapter.in.web.ParticipantController.ParticipantResponse;
import ma.teamslot.match.application.DemandeRejoindre;
import ma.teamslot.match.application.JoinMatchService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCRUM-79 (docs/api/match.yaml : joinMatch, listJoinRequests, acceptJoinRequest).
 * Aucun corps : l'identité du joueur et de l'organisateur vient du claim sub du jeton vérifié.
 */
@RestController
@RequestMapping("/api/v1/matches/{matchId}")
public class JoinController {

    private final JoinMatchService service;

    public JoinController(JoinMatchService service) {
        this.service = service;
    }

    @PostMapping("/join")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JoinRequestResponse demander(@PathVariable UUID matchId, @AuthenticationPrincipal Jwt jeton) {
        return JoinRequestResponse.from(service.demander(Appelant.de(jeton), matchId));
    }

    @GetMapping("/join-requests")
    public List<JoinRequestResponse> demandes(@PathVariable UUID matchId, @AuthenticationPrincipal Jwt jeton) {
        return service.demandes(Appelant.de(jeton), matchId).stream().map(JoinRequestResponse::from).toList();
    }

    @PostMapping("/join-requests/{userId}/accept")
    public ParticipantResponse accepter(@PathVariable UUID matchId, @PathVariable UUID userId,
                                        @AuthenticationPrincipal Jwt jeton) {
        return ParticipantResponse.from(service.accepter(Appelant.de(jeton), matchId, userId));
    }

    public record JoinRequestResponse(UUID matchId, UUID userId, Instant requestedAt) {
        static JoinRequestResponse from(DemandeRejoindre d) {
            return new JoinRequestResponse(d.matchId(), d.userId(), d.requestedAt());
        }
    }
}
