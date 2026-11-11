package ma.teamslot.match.adapter.out.venue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ma.teamslot.match.application.CallerNotAuthenticatedException;
import ma.teamslot.match.application.SlotAlreadyReservedException;
import ma.teamslot.match.application.SlotAlreadyStartedException;
import ma.teamslot.match.application.SlotNotFoundException;
import ma.teamslot.match.application.VenueClient;
import ma.teamslot.match.application.VenueUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Appel synchrone interne à venue-service (POST /api/v1/slot-reservations, docs/api/venue.yaml). */
@Component
public class VenueHttpClient implements VenueClient {

    static final String TYPE_DEJA_COMMENCE = "slot-already-started";

    private final RestClient rest;

    public VenueHttpClient(@Value("${teamslot.venue.base-url:http://venue-service}") String baseUrl) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.rest = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public VenueReservation reserve(UUID matchId, UUID slotId, List<UUID> equipmentIds, String bearerToken) {
        try {
            return rest.post().uri("/api/v1/slot-reservations")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ReserveSlotRequest(slotId, matchId, equipmentIds))
                    .exchange((request, response) -> {
                        int statut = response.getStatusCode().value();
                        if (statut == 401) {
                            throw new CallerNotAuthenticatedException();
                        }
                        if (statut == 409) {
                            throw conflit(slotId, lireType(response));
                        }
                        if (statut == 404) {
                            throw new SlotNotFoundException(slotId);
                        }
                        if (statut != 201) {
                            throw new VenueUnavailableException("venue a répondu " + statut);
                        }
                        return versReservation(response.bodyTo(SlotReservationResponse.class));
                    });
        } catch (RestClientException e) {
            throw new VenueUnavailableException("appel à venue impossible : " + e.getClass().getSimpleName());
        }
    }

    /**
     * SCRUM-97 : venue renvoie deux sortes de 409 (Problem Details, champ type). On ne garde que le type :
     * le texte de venue n'est jamais recopié vers l'organisateur. Type absent ou inconnu : « déjà réservé ».
     */
    static RuntimeException conflit(UUID slotId, String type) {
        if (type != null && type.endsWith("/" + TYPE_DEJA_COMMENCE)) {
            return new SlotAlreadyStartedException(slotId);
        }
        return new SlotAlreadyReservedException(slotId);
    }

    private static String lireType(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
        try {
            ProblemeVenue probleme = response.bodyTo(ProblemeVenue.class);
            return probleme == null ? null : probleme.type();
        } catch (RuntimeException e) {
            return null; // corps vide ou illisible : on garde le message habituel
        }
    }

    private static VenueReservation versReservation(SlotReservationResponse corps) {
        if (corps == null || corps.venueId() == null || corps.totalPrice() == null || corps.startsAt() == null) {
            throw new VenueUnavailableException("réponse de venue incomplète (venueId, totalPrice ou startsAt manquant)");
        }
        return new VenueReservation(corps.venueId(), corps.startsAt(), corps.totalPrice().amount(),
                corps.totalPrice().currency(), corps.acceptsOnSitePayment());
    }

    record ReserveSlotRequest(UUID slotId, UUID matchId, List<UUID> equipmentIds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SlotReservationResponse(UUID venueId, Money totalPrice, boolean acceptsOnSitePayment, Instant startsAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Money(long amount, String currency) {
    }

    /** Seul le champ type du Problem Details de venue est lu (docs/api/venue.yaml). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProblemeVenue(String type) {
    }
}
