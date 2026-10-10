package ma.teamslot.match.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ma.teamslot.match.application.CreateMatchService;
import ma.teamslot.match.domain.Match;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/matches")
public class MatchController {

    private final CreateMatchService service;
    private final String userHeader;

    public MatchController(CreateMatchService service,
                           @Value("${teamslot.auth.user-header:X-User-Id}") String userHeader) {
        this.service = service;
        this.userHeader = userHeader;
    }

    @PostMapping
    public ResponseEntity<MatchResponse> create(@Valid @RequestBody CreateMatchRequest request,
                                                HttpServletRequest http) {
        UUID caller = caller(http);
        Match match = service.create(caller, request.slotId(), request.equipmentIds());
        return ResponseEntity.created(URI.create("/api/v1/matches/" + match.id()))
                .body(MatchResponse.from(match));
    }

    /** L'appelant est posé par la passerelle après contrôle du jeton. Absent ou illisible : 401. */
    private UUID caller(HttpServletRequest http) {
        String value = http.getHeader(userHeader);
        if (value == null || value.isBlank()) {
            throw new MissingCallerException();
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw new MissingCallerException();
        }
    }

    /** Aucun champ « prix » : tout autre champ envoyé par le client est ignoré. */
    public record CreateMatchRequest(@NotNull UUID slotId, List<UUID> equipmentIds) {
    }

    public record MatchResponse(UUID id, String kind, String visibility, int openSpots,
                                String status, UUID ownerId, UUID payerId, UUID slotId,
                                UUID venueId, Instant startsAt, Money totalPrice, long version) {

        public record Money(long amount, String currency) {
        }

        static MatchResponse from(Match m) {
            return new MatchResponse(m.id(), m.kind(), m.visibility(), m.openSpots(), m.status(),
                    m.ownerId(), m.payerId(), m.slotId(), m.venueId(), m.startsAt(),
                    new Money(m.totalAmountCents(), m.currency()), m.version());
        }
    }
}
