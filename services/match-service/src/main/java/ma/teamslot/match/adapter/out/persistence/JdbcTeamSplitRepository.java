package ma.teamslot.match.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ma.teamslot.match.application.MatchPourRepartition;
import ma.teamslot.match.application.ParticipantAvecCote;
import ma.teamslot.match.application.TeamSplitRepository;
import ma.teamslot.match.domain.Participant;
import ma.teamslot.match.domain.ParticipantStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTeamSplitRepository implements TeamSplitRepository {

    /** FOR UPDATE : deux tirages en même temps se suivent, et le match ne change pas de statut pendant le tirage. */
    private static final String VERROU_MATCH = "SELECT owner_id, status FROM matches WHERE id = :matchId FOR UPDATE";

    private static final String INSCRIRE_ORGANISATEUR = """
            INSERT INTO participants (id, match_id, user_id, status, added_by)
            VALUES (:id, :matchId, :userId, 'CONFIRMED', :userId)
            ON CONFLICT DO NOTHING
            """;

    private static final String JOUEURS = """
            SELECT id, match_id, user_id, guest_name, status FROM participants
            WHERE match_id = :matchId AND status IN ('INVITED', 'CONFIRMED')
            ORDER BY created_at, id
            """;

    private static final String EFFACER_COTES = "UPDATE participants SET side = NULL WHERE match_id = :matchId";
    private static final String POSER_COTE =
            "UPDATE participants SET side = :side WHERE match_id = :matchId AND id IN (:ids)";
    private static final String ORGANISATEUR = "SELECT owner_id FROM matches WHERE id = :matchId";

    private static final String COMPTE_PARTICIPANT = """
            SELECT EXISTS (SELECT 1 FROM participants
                           WHERE match_id = :matchId AND user_id = :userId AND status IN ('INVITED', 'CONFIRMED'))
            """;

    private static final String PARTICIPANTS = """
            SELECT id, match_id, user_id, guest_name, status, side FROM participants
            WHERE match_id = :matchId ORDER BY created_at, id
            """;

    private final JdbcClient jdbc;

    public JdbcTeamSplitRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<MatchPourRepartition> verrouillerMatch(UUID matchId) {
        return jdbc.sql(VERROU_MATCH).param("matchId", matchId)
                .query((rs, i) -> new MatchPourRepartition(rs.getObject("owner_id", UUID.class), rs.getString("status")))
                .optional();
    }

    @Override
    public void inscrireOrganisateur(UUID matchId, UUID organisateur) {
        jdbc.sql(INSCRIRE_ORGANISATEUR)
                .param("id", UUID.randomUUID())
                .param("matchId", matchId)
                .param("userId", organisateur)
                .update();
    }

    @Override
    public List<Participant> joueurs(UUID matchId) {
        return jdbc.sql(JOUEURS).param("matchId", matchId).query((rs, i) -> participant(rs)).list();
    }

    @Override
    public void enregistrerCotes(UUID matchId, List<UUID> coteA, List<UUID> coteB) {
        jdbc.sql(EFFACER_COTES).param("matchId", matchId).update();
        poser(matchId, "A", coteA);
        poser(matchId, "B", coteB);
    }

    private void poser(UUID matchId, String cote, List<UUID> ids) {
        if (ids.isEmpty()) {
            return;
        }
        jdbc.sql(POSER_COTE).param("side", cote).param("matchId", matchId).param("ids", ids).update();
    }

    @Override
    public Optional<UUID> organisateur(UUID matchId) {
        return jdbc.sql(ORGANISATEUR).param("matchId", matchId)
                .query((rs, i) -> rs.getObject("owner_id", UUID.class)).optional();
    }

    @Override
    public boolean aUnCompteParticipant(UUID matchId, UUID userId) {
        return jdbc.sql(COMPTE_PARTICIPANT).param("matchId", matchId).param("userId", userId)
                .query(Boolean.class).single();
    }

    @Override
    public List<ParticipantAvecCote> participants(UUID matchId) {
        return jdbc.sql(PARTICIPANTS).param("matchId", matchId)
                .query((rs, i) -> new ParticipantAvecCote(participant(rs), rs.getString("side"))).list();
    }

    private static Participant participant(ResultSet rs) throws SQLException {
        return new Participant(rs.getObject("id", UUID.class), rs.getObject("match_id", UUID.class),
                rs.getObject("user_id", UUID.class), rs.getString("guest_name"),
                ParticipantStatus.valueOf(rs.getString("status")));
    }
}
