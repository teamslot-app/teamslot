package ma.teamslot.payment.adapter.in.web;

import java.util.UUID;
import ma.teamslot.payment.application.CallerNotAuthenticatedException;
import ma.teamslot.payment.application.CollectOnSiteService;
import ma.teamslot.payment.application.PayService;
import ma.teamslot.payment.domain.Payment;
import ma.teamslot.payment.domain.PaymentMode;
import ma.teamslot.payment.domain.PaymentStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/api/payment.yaml : payOnline, payOnSite, confirmCollected, getPayment.
 * Aucun corps de requete : le montant n'est jamais fourni par le client.
 * L'appelant est le "sub" du jeton verifie par SecuriteConfig (ADR-013).
 */
@RestController
@RequestMapping("/api/v1/payments/{matchId}")
public class PaymentController {

    private final PayService service;
    private final CollectOnSiteService encaissement;

    public PaymentController(PayService service, CollectOnSiteService encaissement) {
        this.service = service;
        this.encaissement = encaissement;
    }

    @PostMapping("/online")
    public PaymentResponse payerEnLigne(@PathVariable UUID matchId, @AuthenticationPrincipal Jwt jeton) {
        return PaymentResponse.from(service.payerEnLigne(appelant(jeton), matchId));
    }

    @PostMapping("/on-site")
    public PaymentResponse choisirSurPlace(@PathVariable UUID matchId, @AuthenticationPrincipal Jwt jeton) {
        return PaymentResponse.from(service.choisirSurPlace(appelant(jeton), matchId));
    }

    /** SCRUM-75 : le gérant du terrain confirme l'encaissement sur place. Rôle lu dans le jeton vérifié. */
    @PostMapping("/collected")
    public PaymentResponse confirmerEncaissement(@PathVariable UUID matchId, @AuthenticationPrincipal Jwt jeton) {
        return PaymentResponse.from(encaissement.encaisser(appelant(jeton), jeton.getClaimAsString("role"), matchId));
    }

    @GetMapping
    public PaymentResponse consulter(@PathVariable UUID matchId, @AuthenticationPrincipal Jwt jeton) {
        return PaymentResponse.from(service.consulter(appelant(jeton), matchId));
    }

    private static UUID appelant(Jwt jeton) {
        try {
            return UUID.fromString(jeton.getSubject());
        } catch (RuntimeException e) {
            throw new CallerNotAuthenticatedException();
        }
    }

    /** collectedBy et collectedAt : renseignés après l'encaissement sur place (SCRUM-75). */
    public record PaymentResponse(UUID id, UUID matchId, UUID payerId, PaymentMode mode, Money totalAmount,
                                  PaymentStatus status, UUID collectedBy, String collectedAt) {

        public record Money(long amount, String currency) {
        }

        static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.id(), p.matchId(), p.payerId(), p.mode(),
                    new Money(p.totalAmountCents(), p.currency()), p.status(), p.collectedBy(),
                    p.collectedAt() == null ? null : p.collectedAt().toString());
        }
    }
}
