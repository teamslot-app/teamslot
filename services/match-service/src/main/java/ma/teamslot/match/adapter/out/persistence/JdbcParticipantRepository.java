package ma.teamslot.match.adapter.out.persistence;

import java.sql.Types;
import java.util.Optional;
import java.util.UUID;
import ma.teamslot.match.application.ParticipantAlreadyAddedException;
import ma.teamslot.match.application.ParticipantRepository;
import ma.teamslot.match.domain.Participant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcParticipantRepository implements ParticipantRepository {

    private static final String ORGANISATEUR = "SELECT owner_id FROM matches WHERE id = :matchId";

    private static final String INSERT = """
            INSERT INTO participants (id, match_id, user_id, guest_name, status, added_by)
            VALUES (:id, :matchId, :userId, :guestName, :status, :addedBy)
            """;

    private final JdbcClient jdbc;

    public JdbcParticipantRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> organisateurDe(UUID matchId) {
        return jdbc.sql(ORGANISATEUR).param("matchId", matchId).query(UUID.class).optional();
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
            throw new ParticipantAlreadyAddedException(e);
        }
    }
}
