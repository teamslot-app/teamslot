package ma.teamslot.servicetemplate.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Enveloppe commune de tous les événements TeamSlot (docs/events.md, section 2).
 * Seul le champ data change d'un événement à l'autre.
 */
public record EventEnvelope<T>(
        UUID eventId,
        String type,
        int version,
        Instant occurredAt,
        String producer,
        T data) {

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
    }

    /** Crée une nouvelle enveloppe : nouvel eventId, date actuelle en UTC, version 1. */
    public static <T> EventEnvelope<T> of(String type, String producer, T data) {
        return new EventEnvelope<>(UUID.randomUUID(), type, 1, Instant.now(), producer, data);
    }

    /** N'affiche jamais data : il peut contenir des données personnelles (surnoms d'invités). */
    @Override
    public String toString() {
        return "EventEnvelope[eventId=" + eventId + ", type=" + type + ", version=" + version
                + ", occurredAt=" + occurredAt + ", producer=" + producer + "]";
    }
}