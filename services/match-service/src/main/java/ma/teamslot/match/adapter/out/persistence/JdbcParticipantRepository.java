package ma.teamslot.match.adapter.out.persistence;

import java.sql.Types;
import java.util.Optional;
import java.util.UUID;
import ma.teamslot.match.application.MatchPourAjout;
import ma.teamslot.match.application.ParticipantAlreadyAddedException;
import ma.teamslot.match.application.ParticipantRepository;
import ma.teamslot.match.domain.Participant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcParticipantRepository implements ParticipantRepository {

    /** FOR SHARE : le match ne peut pas changer de statut (annulation) avant la fin de l'ajout. */
    private static final String MATCH = "SELECT owner_id, status FROM matches WHERE id = :matchId FOR SHARE";
    private static final String INDEX_SURNOM = "uq_participants_match_surnom";

    private static final String INSERT = """
            INSERT INTO participants (id, match_id, user_id, guest_name, status, added_by)
            VALUES (:id, :matchId, :userId, :guestName, :status, :addedBy)
            """;

    private final JdbcClient jdbc;

    public JdbcParticipantRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<MatchPourAjout> matchPourAjout(UUID matchId) {
        return jdbc.sql(MATCH).param("matchId", matchId)
                .query((rs, ligne) -> new MatchPourAjout(rs.getObject("owner_id", UUID.class), rs.getString("status")))
                .optional();
    }

    @Override
    public void save(Participant p, UUID ajoutePar) {
        try {
            jdbc.sql(INSERT)
                    .param("id", p.id())
                    .param("matchId", p.matchId())
                    .param("userId", p.userId(), Types.OTHER)
                    .param("guestName", p.guestName(), Types.VARCHAR)
                    .param("status", p.status().name())
                    .param("addedBy", ajoutePar)
                    .update();
        } catch (DuplicateKeyException e) {
            String message = String.valueOf(e.getMessage());
            throw new ParticipantAlreadyAddedException(message.contains(INDEX_SURNOM)
                    ? ParticipantAlreadyAddedException.Raison.SURNOM
                    : ParticipantAlreadyAddedException.Raison.COMPTE, e);
        }
    }
}
