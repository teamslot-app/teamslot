package ma.teamslot.events.internal;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import org.springframework.core.env.Environment;

/**
 * SCRUM-147 — SQL de l'outbox et de l'idempotence selon la base du service (ADR-017).
 * PostgreSQL (venue, payment…) : SQL d'origine, inchangé. MySQL (identity) : identifiants en CHAR(36), payload en
 * JSON, horodatages DATETIME(6) en UTC, INSERT IGNORE au lieu de ON CONFLICT.
 * Choisi par la propriété teamslot.events.sql (postgresql par défaut) : explicite, pas de détection au démarrage.
 */
enum DialecteSql {

    POSTGRESQL(
            """
            INSERT INTO outbox_event (id, aggregate_id, event_type, payload, occurred_at)
            VALUES (:id, :aggregateId, :type, CAST(:payload AS JSONB), :occurredAt)
            """,
            """
            SELECT id, aggregate_id, event_type, CAST(payload AS TEXT) AS payload
            FROM outbox_event
            WHERE published_at IS NULL
            ORDER BY position
            LIMIT :taille
            FOR UPDATE SKIP LOCKED
            """,
            "UPDATE outbox_event SET published_at = now() WHERE id = :id",
            """
            INSERT INTO processed_event (event_id) VALUES (:eventId)
            ON CONFLICT (event_id) DO NOTHING
            """),

    MYSQL(
            """
            INSERT INTO outbox_event (id, aggregate_id, event_type, payload, occurred_at)
            VALUES (:id, :aggregateId, :type, :payload, :occurredAt)
            """,
            """
            SELECT id, aggregate_id, event_type, payload
            FROM outbox_event
            WHERE published_at IS NULL
            ORDER BY position
            LIMIT :taille
            FOR UPDATE SKIP LOCKED
            """,
            "UPDATE outbox_event SET published_at = UTC_TIMESTAMP(6) WHERE id = :id",
            // INSERT IGNORE : 0 ligne insérée si l'eventId est déjà là (doublon), 1 sinon
            "INSERT IGNORE INTO processed_event (event_id) VALUES (:eventId)");

    static final String PROPRIETE = "teamslot.events.sql";

    final String inserer;
    final String selectionnerAPublier;
    final String marquerPublie;
    final String marquerTraite;

    DialecteSql(String inserer, String selectionnerAPublier, String marquerPublie, String marquerTraite) {
        this.inserer = inserer;
        this.selectionnerAPublier = selectionnerAPublier;
        this.marquerPublie = marquerPublie;
        this.marquerTraite = marquerTraite;
    }

    /** Valeur à lier pour un identifiant : UUID natif en PostgreSQL, texte de 36 caractères en MySQL. */
    Object identifiant(UUID id) {
        return this == MYSQL ? id.toString() : id;
    }

    /** Valeur à lier pour un instant : timestamptz en PostgreSQL, DATETIME(6) en UTC en MySQL. */
    Object horodatage(Instant instant) {
        return this == MYSQL ? LocalDateTime.ofInstant(instant, ZoneOffset.UTC) : instant.atOffset(ZoneOffset.UTC);
    }

    static DialecteSql depuis(Environment environnement) {
        String valeur = environnement.getProperty(PROPRIETE, "postgresql").trim().toLowerCase(Locale.ROOT);
        return switch (valeur) {
            case "postgresql" -> POSTGRESQL;
            case "mysql" -> MYSQL;
            default -> throw new IllegalStateException(PROPRIETE + " doit valoir postgresql ou mysql, pas : " + valeur);
        };
    }
}
