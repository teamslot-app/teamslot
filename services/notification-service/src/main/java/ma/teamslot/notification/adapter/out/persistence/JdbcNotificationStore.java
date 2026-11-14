package ma.teamslot.notification.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import ma.teamslot.notification.application.NotificationStore;
import ma.teamslot.notification.domain.TypeNotification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Tables de V3__notifications.sql (PostgreSQL, base notification, ADR-017). */
@Repository
class JdbcNotificationStore implements NotificationStore {

    /** Essais d'envoi Discord avant abandon (environ 1 h 30 en tout avec le délai croissant). */
    static final int ESSAIS_MAX = 8;
    private final JdbcClient jdbc;
    /** Premier délai avant un nouvel essai, doublé à chaque échec : 30 s, 1 min, 2 min, 4 min… */
    private final long delaiInitialS;

    /** Nom du paramètre :match dans les requêtes SQL. */
    private static final String P_MATCH = "match";

    JdbcNotificationStore(JdbcClient jdbc,
                          @Value("${teamslot.notification.discord.delai-initial-s:30}") long delaiInitialS) {
        this.jdbc = jdbc;
        this.delaiInitialS = delaiInitialS;
    }

    @Override
    public void ajouterParticipant(UUID matchId, UUID userId) {
        jdbc.sql("""
                INSERT INTO match_participant (match_id, user_id) VALUES (:match, :user)
                ON CONFLICT DO NOTHING
                """).param(P_MATCH, matchId).param("user", userId).update();
    }

    @Override
    public List<UUID> participants(UUID matchId) {
        return jdbc.sql("SELECT user_id FROM match_participant WHERE match_id = :match ORDER BY user_id")
                .param(P_MATCH, matchId).query(UUID.class).list();
    }

    @Override
    public void enregistrerNotification(UUID userId, TypeNotification type, UUID matchId) {
        jdbc.sql("INSERT INTO notification (id, user_id, type, match_id) VALUES (:id, :user, :type, :match)")
                .param("id", UUID.randomUUID()).param("user", userId).param("type", type.name())
                .param(P_MATCH, matchId).update();
    }

    @Override
    public void enregistrerMessageDiscord(String contenu) {
        jdbc.sql("INSERT INTO discord_message (id, contenu) VALUES (:id, :contenu)")
                .param("id", UUID.randomUUID()).param("contenu", contenu).update();
    }

    @Override
    public List<MessageAEnvoyer> messagesAEnvoyer(int limite) {
        return jdbc.sql("""
                SELECT id, contenu, essais FROM discord_message
                WHERE sent_at IS NULL AND NOT abandonne AND prochain_essai <= now()
                ORDER BY created_at
                LIMIT :limite
                FOR UPDATE SKIP LOCKED
                """).param("limite", limite)
                .query((ligne, n) -> new MessageAEnvoyer(ligne.getObject("id", UUID.class),
                        ligne.getString("contenu"), ligne.getInt("essais")))
                .list();
    }

    @Override
    public void marquerEnvoye(UUID messageId) {
        jdbc.sql("UPDATE discord_message SET sent_at = now() WHERE id = :id").param("id", messageId).update();
    }

    @Override
    public void noterEchec(UUID messageId, int essaisDejaFaits) {
        int essais = essaisDejaFaits + 1;
        long delai = delaiInitialS << Math.min(essaisDejaFaits, 10);
        jdbc.sql("""
                UPDATE discord_message
                SET essais = :essais, abandonne = :abandonne,
                    prochain_essai = now() + (:delai * INTERVAL '1 second')
                WHERE id = :id
                """).param("essais", essais).param("abandonne", essais >= ESSAIS_MAX)
                .param("delai", delai).param("id", messageId).update();
    }
}
