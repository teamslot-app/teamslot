package ma.teamslot.payment.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import ma.teamslot.payment.application.PaymentRepository;
import ma.teamslot.payment.domain.Payment;
import ma.teamslot.payment.domain.PaymentMode;
import ma.teamslot.payment.domain.PaymentStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPaymentRepository implements PaymentRepository {

    private static final String MATCH_ID = "matchId";
    private static final String STATUS = "status";

    private static final String INSERT_SI_ABSENT = """
            INSERT INTO payments (id, match_id, payer_id, total_amount_cents, currency,
                                  accepts_on_site_payment, status, venue_id)
            VALUES (:id, :matchId, :payerId, :total, :currency, :onSite, :status, :venueId)
            ON CONFLICT (match_id) DO NOTHING
            """;

    private static final String SELECT = """
            SELECT id, match_id, payer_id, total_amount_cents, currency, accepts_on_site_payment, mode, status,
                   venue_id, collected_by, collected_at
            FROM payments WHERE match_id = :matchId
            """;

    private static final String UPDATE = """
            UPDATE payments SET mode = :mode, status = :status, collected_by = :collectedBy,
                                collected_at = :collectedAt, updated_at = now()
            WHERE id = :id
            """;

    private final JdbcClient jdbc;

    public JdbcPaymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean creerSiAbsent(Payment p) {
        return jdbc.sql(INSERT_SI_ABSENT)
                .param("id", p.id())
                .param(MATCH_ID, p.matchId())
                .param("payerId", p.payerId())
                .param("total", p.totalAmountCents())
                .param("currency", p.currency())
                .param("onSite", p.acceptsOnSitePayment())
                .param(STATUS, p.status().name())
                .param("venueId", p.venueId(), Types.OTHER)
                .update() == 1;
    }

    @Override
    public Optional<Payment> trouver(UUID matchId) {
        return jdbc.sql(SELECT).param(MATCH_ID, matchId).query(JdbcPaymentRepository::lire).optional();
    }

    @Override
    public Optional<Payment> trouverPourMiseAJour(UUID matchId) {
        return jdbc.sql(SELECT + " FOR UPDATE").param(MATCH_ID, matchId)
                .query(JdbcPaymentRepository::lire).optional();
    }

    @Override
    public void mettreAJour(Payment p) {
        jdbc.sql(UPDATE)
                .param("id", p.id())
                .param("mode", p.mode() == null ? null : p.mode().name(), Types.VARCHAR)
                .param(STATUS, p.status().name())
                .param("collectedBy", p.collectedBy(), Types.OTHER)
                .param("collectedAt", p.collectedAt() == null ? null : p.collectedAt().atOffset(ZoneOffset.UTC),
                        Types.TIMESTAMP_WITH_TIMEZONE)
                .update();
    }

    private static Payment lire(ResultSet rs, int ligne) throws SQLException {
        String mode = rs.getString("mode");
        OffsetDateTime encaisseLe = rs.getObject("collected_at", OffsetDateTime.class);
        Instant collectedAt = encaisseLe == null ? null : encaisseLe.toInstant();
        return new Payment(rs.getObject("id", UUID.class), rs.getObject("match_id", UUID.class),
                rs.getObject("payer_id", UUID.class), rs.getLong("total_amount_cents"),
                rs.getString("currency"), rs.getBoolean("accepts_on_site_payment"),
                mode == null ? null : PaymentMode.valueOf(mode), PaymentStatus.valueOf(rs.getString("status")),
                rs.getObject("venue_id", UUID.class), rs.getObject("collected_by", UUID.class), collectedAt);
    }
}
