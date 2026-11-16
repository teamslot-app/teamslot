package ma.teamslot.match.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ma.teamslot.match.application.CancelMatchService;
import ma.teamslot.match.application.CreateMatchService;
import ma.teamslot.match.application.ListMyMatchesService;
import ma.teamslot.match.application.OpenSpotsService;
import ma.teamslot.match.domain.Match;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/matches")
public class MatchController {

    private final CreateMatchService service;
    private final CancelMatchService annulation;
    private final OpenSpotsService places;
    private final ListMyMatchesService consultation;

    public MatchController(CreateMatchService service, CancelMatchService annulation, OpenSpotsService places,
                           ListMyMatchesService consultation) {
        this.service = service;
        this.annulation = annulation;
        this.places = places;
        this.consultation = consultation;
    }

    /** SCRUM-77 : mes matchs à venir. Identité lue dans le JWT seulement ; données personnelles, jamais en cache. */
    @GetMapping
    public ResponseEntity<List<MatchResponse>> mesMatchs(@AuthenticationPrincipal Jwt jeton) {
        List<MatchResponse> matchs = consultation.aVenir(Appelant.de(jeton)).stream()
                .map(MatchResponse::from)
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(matchs);
    }

    /**
     * SCRUM-77 : détail d'un match pour l'organisateur et les participants avec compte ; 404 pour les autres.
     * « /open » (OpenMatchesController) est un chemin fixe : Spring le préfère toujours à « /{matchId} ».
     */
    @GetMapping("/{matchId}")
    public ResponseEntity<MatchResponse> detail(@PathVariable UUID matchId, @AuthenticationPrincipal Jwt jeton) {
        Match match = consultation.detail(Appelant.de(jeton), matchId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(MatchResponse.from(match));
    }

    /** SCRUM-78 : l'organisateur fixe le nombre de places ouvertes à des joueurs extérieurs. */
    @PostMapping("/{matchId}/open-spots")
    public MatchResponse openSpots(@PathVariable UUID matchId, @Valid @RequestBody OpenSpotsRequest request,
                                   @AuthenticationPrincipal Jwt jeton) {
        return MatchResponse.from(places.open(Appelant.de(jeton), matchId, request.openSpots()));
    }

    /** SCRUM-76 : l'organisateur (claim sub du JWT, jamais un en-tête) annule le match. */
    @PostMapping("/{matchId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID matchId, @AuthenticationPrincipal Jwt jeton) {
        annulation.cancel(Appelant.de(jeton), matchId);
    }

    @PostMapping
    public ResponseEntity<MatchResponse> create(@Valid @RequestBody CreateMatchRequest request,
                                                @AuthenticationPrincipal Jwt jeton) {
        // Le jeton verifie est transmis tel quel a venue-service, qui le verifie aussi (ADR-013).
        Match match = service.create(Appelant.de(jeton), jeton.getTokenValue(), request.slotId(),
                request.equipmentIds());
        return ResponseEntity.created(URI.create("/api/v1/matches/" + match.id()))
                .body(MatchResponse.from(match));
    }

    /** Aucun champ « prix » : tout autre champ envoyé par le client est ignoré. */
    public record CreateMatchRequest(@NotNull UUID slotId, List<UUID> equipmentIds) {
    }

    /** Integer et @NotNull : un corps sans openSpots donne 400, pas 0. */
    public record OpenSpotsRequest(@NotNull @Min(1) Integer openSpots) {
    }

    public record MatchResponse(UUID id, String kind, String visibility, int openSpots, int maxPlayers,
                                String status, UUID ownerId, UUID payerId, UUID slotId,
                                UUID venueId, Instant startsAt, Money totalPrice, long version) {

        public record Money(long amount, String currency) {
        }

        static MatchResponse from(Match m) {
            return new MatchResponse(m.id(), m.kind(), m.visibility(), m.openSpots(), m.maxPlayers(), m.status(),
                    m.ownerId(), m.payerId(), m.slotId(), m.venueId(), m.startsAt(),
                    new Money(m.totalAmountCents(), m.currency()), m.version());
        }
    }
}
