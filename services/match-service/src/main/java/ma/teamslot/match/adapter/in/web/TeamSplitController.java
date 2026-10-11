package ma.teamslot.match.adapter.in.web;

import java.util.List;
import java.util.UUID;
import ma.teamslot.match.adapter.in.web.ParticipantController.ParticipantResponse;
import ma.teamslot.match.application.ListParticipantsService;
import ma.teamslot.match.application.SplitTeamsService;
import ma.teamslot.match.domain.Participant;
import ma.teamslot.match.domain.TeamSplit;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCRUM-61 (docs/api/match.yaml) : POST /split (splitTeams) et GET /participants (listParticipants).
 * Classe à part : ParticipantController (SCRUM-60) reste inchangé.
 */
@RestController
@RequestMapping("/api/v1/matches/{matchId}")
public class TeamSplitController {

    private final SplitTeamsService split;
    private final ListParticipantsService list;

    public TeamSplitController(SplitTeamsService split, ListParticipantsService list) {
        this.split = split;
        this.list = list;
    }

    @PostMapping("/split")
    public TeamSplitResponse split(@PathVariable UUID matchId, @RequestParam("strategy") String strategy,
                                   @AuthenticationPrincipal Jwt jeton) {
        TeamSplit resultat = split.split(Appelant.de(jeton), matchId, strategy);
        return new TeamSplitResponse(matchId, strategy, vue(resultat.sideA(), "A"), vue(resultat.sideB(), "B"));
    }

    @GetMapping("/participants")
    public List<ParticipantResponse> participants(@PathVariable UUID matchId, @AuthenticationPrincipal Jwt jeton) {
        return list.list(Appelant.de(jeton), matchId).stream()
                .map(p -> reponse(p.participant(), p.side()))
                .toList();
    }

    public record TeamSplitResponse(UUID matchId, String strategy, List<ParticipantResponse> sideA,
                                    List<ParticipantResponse> sideB) {
    }

    private static List<ParticipantResponse> vue(List<Participant> joueurs, String cote) {
        return joueurs.stream().map(p -> reponse(p, cote)).toList();
    }

    private static ParticipantResponse reponse(Participant p, String cote) {
        return new ParticipantResponse(p.id(), p.matchId(), p.userId(), p.guestName(), cote, null, p.status());
    }
}
