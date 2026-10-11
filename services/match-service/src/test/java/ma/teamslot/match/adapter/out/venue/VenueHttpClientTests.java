package ma.teamslot.match.adapter.out.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ma.teamslot.match.application.CallerNotAuthenticatedException;
import ma.teamslot.match.application.SlotAlreadyReservedException;
import ma.teamslot.match.application.SlotNotFoundException;
import ma.teamslot.match.application.VenueClient.VenueReservation;
import ma.teamslot.match.application.VenueUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Le client HTTP contre un petit serveur local qui joue le rôle de venue-service. */
class VenueHttpClientTests {

    private static final String RESERVATION = """
            {"slotId":"%s","matchId":"%s","venueId":"%s",
             "totalPrice":{"amount":30000,"currency":"MAD"},
             "acceptsOnSitePayment":true,"status":"RESERVED",
             "startsAt":"2026-10-20T18:00:00Z","endsAt":"2026-10-20T19:00:00Z"}
            """;

    private HttpServer server;
    private volatile String autorisationRecue;

    private VenueHttpClient demarrer(int statut, String corps) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/slot-reservations", exchange -> {
            autorisationRecue = exchange.getRequestHeaders().getFirst("Authorization");
            exchange.getRequestBody().readAllBytes();
            byte[] octets = corps.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statut, octets.length == 0 ? -1 : octets.length);
            if (octets.length > 0) {
                exchange.getResponseBody().write(octets);
            }
            exchange.close();
        });
        server.start();
        return new VenueHttpClient("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void arreter() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void reservation_creee_est_convertie() throws Exception {
        UUID venueId = UUID.randomUUID();
        String corps = RESERVATION.formatted(UUID.randomUUID(), UUID.randomUUID(), venueId);

        VenueReservation r = demarrer(201, corps).reserve(UUID.randomUUID(), UUID.randomUUID(), List.of(), "jeton");

        assertThat(r.venueId()).isEqualTo(venueId);
        assertThat(r.startsAt()).isEqualTo(Instant.parse("2026-10-20T18:00:00Z"));
        assertThat(r.totalAmountCents()).isEqualTo(30000L);
        assertThat(r.currency()).isEqualTo("MAD");
        assertThat(r.acceptsOnSitePayment()).isTrue();
    }

    @Test
    void le_jeton_est_transmis_a_venue() throws Exception {
        String corps = RESERVATION.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        demarrer(201, corps).reserve(UUID.randomUUID(), UUID.randomUUID(), List.of(), "mon.jeton.signe");

        assertThat(autorisationRecue).isEqualTo("Bearer mon.jeton.signe");
    }

    @Test
    void jeton_refuse_par_venue_401() throws Exception {
        VenueHttpClient client = demarrer(401, "{}");
        UUID m = UUID.randomUUID();
        UUID s = UUID.randomUUID();
        assertThatThrownBy(() -> client.reserve(m, s, List.of(), "jeton"))
                .isInstanceOf(CallerNotAuthenticatedException.class);
    }

    @Test
    void creneau_pris_409() throws Exception {
        VenueHttpClient client = demarrer(409, "{}");
        UUID m = UUID.randomUUID();
        UUID s = UUID.randomUUID();
        assertThatThrownBy(() -> client.reserve(m, s, List.of(), "jeton"))
                .isInstanceOf(SlotAlreadyReservedException.class);
    }

    @Test
    void creneau_inconnu_404() throws Exception {
        VenueHttpClient client = demarrer(404, "{}");
        UUID m = UUID.randomUUID();
        UUID s = UUID.randomUUID();
        assertThatThrownBy(() -> client.reserve(m, s, List.of(), "jeton"))
                .isInstanceOf(SlotNotFoundException.class);
    }

    @Test
    void erreur_500_de_venue_est_une_indisponibilite() throws Exception {
        VenueHttpClient client = demarrer(500, "{}");
        UUID m = UUID.randomUUID();
        UUID s = UUID.randomUUID();
        assertThatThrownBy(() -> client.reserve(m, s, List.of(), "jeton"))
                .isInstanceOf(VenueUnavailableException.class);
    }

    @Test
    void reponse_sans_heure_de_debut_est_refusee() throws Exception {
        String sansDebut = """
                {"venueId":"%s","totalPrice":{"amount":1,"currency":"MAD"},"acceptsOnSitePayment":false}
                """.formatted(UUID.randomUUID());
        VenueHttpClient client = demarrer(201, sansDebut);
        UUID m = UUID.randomUUID();
        UUID s = UUID.randomUUID();
        assertThatThrownBy(() -> client.reserve(m, s, List.of(), "jeton"))
                .isInstanceOf(VenueUnavailableException.class);
    }

    @Test
    void venue_injoignable() {
        VenueHttpClient client = new VenueHttpClient("http://127.0.0.1:1");
        UUID m = UUID.randomUUID();
        UUID s = UUID.randomUUID();
        assertThatThrownBy(() -> client.reserve(m, s, List.of(), "jeton"))
                .isInstanceOf(VenueUnavailableException.class);
    }
}
