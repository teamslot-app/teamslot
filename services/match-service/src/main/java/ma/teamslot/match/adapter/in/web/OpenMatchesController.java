package ma.teamslot.match.adapter.in.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.teamslot.match.application.ListOpenMatchesService;
import ma.teamslot.match.application.MatchOuvert;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCRUM-118 : GET /api/v1/matches/open?date= (docs/api/match.yaml, listOpenMatches), provisoire (ADR-012).
 * Contrôleur séparé de MatchController : « /open » est un chemin fixe, prioritaire sur « /{id} ».
 * Utilisateur connecté seulement (SecuriteConfig) ; la réponse ne contient aucun participant.
 */
@RestController
public class OpenMatchesController {

    private final ListOpenMatchesService service;

    public OpenMatchesController(ListOpenMatchesService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/matches/open")
    public List<OpenMatchResponse> ouverts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.ouverts(date).stream().map(OpenMatchResponse::from).toList();
    }

    public record OpenMatchResponse(UUID id, UUID venueId, Instant startsAt, int openSpots, int maxPlayers) {
        static OpenMatchResponse from(MatchOuvert m) {
            return new OpenMatchResponse(m.id(), m.venueId(), m.startsAt(), m.openSpots(), m.maxPlayers());
        }
    }
}
