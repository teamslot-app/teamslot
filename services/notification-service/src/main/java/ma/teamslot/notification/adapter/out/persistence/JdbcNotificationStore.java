package ma.teamslot.notification.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import ma.teamslot.notification.application.NotificationStore;
import ma.teamslot.notification.domain.Notification;
import ma.teamslot.notification.domain.Repere;
import ma.teamslot.notification.domain.TypeNotification;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Tables de V3__notifications.sql (PostgreSQL, base notification, ADR-017). */
@Repository
class JdbcNotificationStore implements NotificationStore {

    /** Noms des paramètres :match, :user et :nombre dans les requêtes SQL. */
    private static final String P_MATCH = "match";
    private static final String P_USER = "user";
    private static final String P_NOMBRE = "nombre";

    private final JdbcClient jdbc;

    JdbcNotificationStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void ajouterParticipant(UUID matchId, UUID userId) {
        jdbc.sql("""
                INSERT INTO match_participant (match_id, user_id) VALUES (:match, :user)
                ON CONFLICT DO NOTHING
                """).param(P_MATCH, matchId).param(P_USER, userId).update();
    }

    @Override
    public List<UUID> participants(UUID matchId) {
        return jdbc.sql("SELECT user_id FROM match_participant WHERE match_id = :match ORDER BY user_id")
                .param(P_MATCH, matchId).query(UUID.class).list();
    }

    @Override
    public void enregistrerNotification(UUID userId, TypeNotification type, UUID matchId) {
        jdbc.sql("INSERT INTO notification (id, user_id, type, match_id) VALUES (:id, :user, :type, :match)")
                .param("id", UUID.randomUUID()).param(P_USER, userId).param("type", type.name())
                .param(P_MATCH, matchId).update();
    }

    @Override
    public List<Notification> notificationsDe(UUID userId, Repere apres, int nombre) {
        // Pagination par curseur (keyset) : index idx_notification_user_recentes (user_id, created_at DESC, id DESC).
        // Le filtre user_id est TOUJOURS appliqué : un curseur modifié à la main ne montre que ses propres notifications.
        if (apres == null) {
            return jdbc.sql("""
                    SELECT id, type, match_id, created_at, read_at FROM notification
                    WHERE user_id = :user
                    ORDER BY created_at DESC, id DESC
                    LIMIT :nombre
                    """).param(P_USER, userId).param(P_NOMBRE, nombre)
                    .query(JdbcNotificationStore::notification).list();
        }
        return jdbc.sql("""
                SELECT id, type, match_id, created_at, read_at FROM notification
                WHERE user_id = :user AND (created_at, id) < (:creeLe, :id)
                ORDER BY created_at DESC, id DESC
                LIMIT :nombre
                """).param(P_USER, userId).param("creeLe", OffsetDateTime.ofInstant(apres.creeLe(), ZoneOffset.UTC))
                .param("id", apres.id()).param(P_NOMBRE, nombre)
                .query(JdbcNotificationStore::notification).list();
    }

    @Override
    public boolean marquerLue(UUID notificationId, UUID userId) {
        // COALESCE : une notification déjà lue garde sa première date (route idempotente).
        return jdbc.sql("""
                UPDATE notification SET read_at = COALESCE(read_at, now())
                WHERE id = :id AND user_id = :user
                """).param("id", notificationId).param(P_USER, userId).update() == 1;
    }

    private static Notification notification(ResultSet ligne, int numero) throws SQLException {
        OffsetDateTime lue = ligne.getObject("read_at", OffsetDateTime.class);
        return new Notification(ligne.getObject("id", UUID.class),
                TypeNotification.valueOf(ligne.getString("type")),
                ligne.getObject("match_id", UUID.class),
                ligne.getObject("created_at", OffsetDateTime.class).toInstant(),
                lue == null ? null : lue.toInstant());
    }
}
