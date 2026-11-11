package ma.teamslot.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Enveloppe commune de tous les événements TeamSlot (docs/events.md, section 2).
 * Seul le champ data change d'un événement à l'autre.
 * correlationId (SCRUM-89) est facultatif : absent des événements écrits avant, ou hors requête HTTP.
 * Il n'est écrit dans le JSON que s'il existe : les anciens consommateurs ne voient aucune différence.
 */
public record EventEnvelope<T>(
        UUID eventId,
        String type,
        int version,
        Instant occurredAt,
        String producer,
        T data,
        @JsonInclude(JsonInclude.Include.NON_NULL) String correlationId) {

    /** Format du catalogue : exactement domaine.evenement, en minuscules. Longueurs bornées, sans groupe répété (Sonar java:S5998). */
    private static final Pattern TYPE_FORMAT = Pattern.compile("^[a-z]{1,30}\\.[a-z_]{1,60}$");

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId est obligatoire");
        Objects.requireNonNull(type, "type est obligatoire");
        Objects.requireNonNull(occurredAt, "occurredAt est obligatoire");
        Objects.requireNonNull(producer, "producer est obligatoire");
        Objects.requireNonNull(data, "data est obligatoire");
        if (!TYPE_FORMAT.matcher(type).matches()) {
            throw new IllegalArgumentException("type invalide, format attendu domaine.evenement : " + type);
        }
        if (version < 1) {
            throw new IllegalArgumentException("version doit valoir au moins 1");
        }
        // Valeur qui n'est pas un UUID (message mal formé) : ignorée, l'événement n'est pas rejeté.
        correlationId = Correlation.valide(correlationId);
    }

    /**
     * Crée une nouvelle enveloppe : nouvel eventId, date actuelle en UTC, version 1,
     * et l'identifiant de corrélation de la requête en cours (MDC), s'il y en a un.
     */
    public static <T> EventEnvelope<T> of(String type, String producer, T data) {
        return new EventEnvelope<>(UUID.randomUUID(), type, 1, Instant.now(), producer, data, Correlation.courante());
    }

    /** N'affiche jamais data : il peut contenir des données personnelles (surnoms d'invités). */
    @Override
    public String toString() {
        return "EventEnvelope[eventId=" + eventId + ", type=" + type + ", version=" + version
                + ", occurredAt=" + occurredAt + ", producer=" + producer + ", correlationId=" + correlationId + "]";
    }
}
