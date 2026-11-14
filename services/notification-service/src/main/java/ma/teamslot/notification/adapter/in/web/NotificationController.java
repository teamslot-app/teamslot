package ma.teamslot.notification.adapter.in.web;

import java.util.List;
import java.util.UUID;
import ma.teamslot.notification.application.NotificationService;
import ma.teamslot.notification.application.NotificationService.Tranche;
import ma.teamslot.notification.domain.Notification;
import ma.teamslot.notification.domain.TypeNotification;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/api/notification.yaml (SCRUM-71) : listMyNotifications et markNotificationRead (SCRUM-81).
 * L'utilisateur est toujours le "sub" du jeton vérifié par SecuriteConfig, jamais un paramètre (ADR-013).
 * Données privées : Cache-Control no-store sur chaque réponse.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    static final String LIMITE_PAR_DEFAUT = "20";
    static final int LIMITE_MAX = 50;

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<NotificationSlice> mesNotifications(
            @AuthenticationPrincipal Jwt jeton,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", defaultValue = LIMITE_PAR_DEFAUT) int limit) {
        if (limit < 1 || limit > LIMITE_MAX) {
            throw new LimiteInvalideException();
        }
        Tranche tranche = service.mesNotifications(appelant(jeton), Curseur.lire(cursor), limit);
        NotificationSlice corps = new NotificationSlice(
                tranche.items().stream().map(NotificationResponse::de).toList(),
                tranche.suivante() == null ? null : Curseur.ecrire(tranche.suivante()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(corps);
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> marquerLue(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID notificationId) {
        service.marquerLue(notificationId, appelant(jeton));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static UUID appelant(Jwt jeton) {
        try {
            return UUID.fromString(jeton.getSubject());
        } catch (RuntimeException e) {
            throw new AppelantNonAuthentifieException();
        }
    }

    /** Schéma NotificationSlice : nextCursor null quand il n'y a plus de notifications. */
    public record NotificationSlice(List<NotificationResponse> items, String nextCursor) {
    }

    /** Schéma Notification : dates ISO 8601 UTC, readAt null tant que la notification n'est pas lue. */
    public record NotificationResponse(UUID id, TypeNotification type, UUID matchId, String createdAt, String readAt) {

        static NotificationResponse de(Notification n) {
            return new NotificationResponse(n.id(), n.type(), n.matchId(), n.creeLe().toString(),
                    n.lueLe() == null ? null : n.lueLe().toString());
        }
    }

    static final class LimiteInvalideException extends RuntimeException {
        LimiteInvalideException() {
            super("limit hors limites");
        }
    }

    static final class AppelantNonAuthentifieException extends RuntimeException {
        AppelantNonAuthentifieException() {
            super("sub du jeton illisible");
        }
    }
}
