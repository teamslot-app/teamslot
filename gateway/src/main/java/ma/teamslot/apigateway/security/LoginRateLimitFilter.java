package ma.teamslot.apigateway.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * SCRUM-67 — limitation de débit sur la connexion (test de sécurité S4, force brute).
 *
 * Compte les tentatives de POST /api/v1/auth/login par adresse IP, sur une fenêtre fixe.
 * Au-delà du maximum : 429 Too Many Requests, en-tête Retry-After, erreur Problem Details.
 *
 * Compteur en mémoire : suffisant avec une seule passerelle. Avec plusieurs réplicas,
 * il faudra un compteur partagé (Redis, prévu par la conception).
 */
@Component
@Order(-200) // avant la chaîne Spring Security (ordre -100)
public class LoginRateLimitFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitFilter.class);
    static final String LOGIN_PATH = "/api/v1/auth/login";
    /** Au-delà de ce nombre d'adresses suivies, on fait le ménage des fenêtres expirées. */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public LoginRateLimitFilter(@Value("${teamslot.rate-limit.login.max-attempts:5}") int maxAttempts,
                                @Value("${teamslot.rate-limit.login.window-seconds:60}") long windowSeconds) {
        this(maxAttempts, Duration.ofSeconds(windowSeconds), Clock.systemUTC());
    }

    /** Constructeur utilisé par les tests, avec une horloge contrôlée. */
    LoginRateLimitFilter(int maxAttempts, Duration window, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        boolean isLogin = HttpMethod.POST.equals(request.getMethod())
                && LOGIN_PATH.equals(request.getPath().pathWithinApplication().value());
        if (!isLogin) {
            return chain.filter(exchange);
        }
        String client = clientKey(request);
        if (tryAcquire(client)) {
            return chain.filter(exchange);
        }
        log.warn("Limite de tentatives de connexion atteinte pour le client {}", client);
        return tooManyRequests(exchange);
    }

    /** Vrai si la tentative est autorisée, faux si le client a dépassé la limite. */
    boolean tryAcquire(String client) {
        Instant now = clock.instant();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> w.isExpired(now, window));
        }
        Window current = windows.compute(client, (key, existing) ->
                (existing == null || existing.isExpired(now, window)) ? new Window(now) : existing);
        return current.attempts.incrementAndGet() <= maxAttempts;
    }

    /**
     * Identifie le client par son adresse IP.
     * Derrière l'Ingress (SCRUM-34), l'adresse vue sera celle de l'Ingress : il faudra alors
     * lire X-Forwarded-For, mais seulement s'il est posé par l'Ingress (sinon falsifiable).
     */
    private static String clientKey(ServerHttpRequest request) {
        return request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "inconnu";
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(window.toSeconds()));
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        byte[] body = ("{\"type\":\"https://teamslot.app/problems/too-many-requests\","
                + "\"title\":\"Trop de tentatives\",\"status\":429,"
                + "\"detail\":\"Trop de tentatives de connexion. Réessayez plus tard.\"}")
                .getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    /** Fenêtre de comptage d'un client : début de la fenêtre et nombre de tentatives. */
    private static final class Window {
        private final Instant start;
        private final AtomicInteger attempts = new AtomicInteger();

        Window(Instant start) {
            this.start = start;
        }

        boolean isExpired(Instant now, Duration window) {
            return !now.isBefore(start.plus(window));
        }
    }
}
