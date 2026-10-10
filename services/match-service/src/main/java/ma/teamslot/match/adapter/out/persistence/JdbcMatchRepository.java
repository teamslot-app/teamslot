package ma.teamslot.match.adapter.out.persistence;

import java.time.ZoneOffset;
import ma.teamslot.match.application.MatchRepository;
import ma.teamslot.match.domain.Match;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMatchRepository implements MatchRepository {

    private static final String INSERT = """
            INSERT INTO matches (id, kind, status, visibility, open_spots, owner_id, payer_id,
                                 venue_id, slot_id, starts_at, total_amount_cents, currency,
                                 accepts_on_site_payment, version)
            VALUES (:id, :kind, :status, :visibility, :openSpots, :ownerId, :payerId,
                    :venueId, :slotId, :startsAt, :total, :currency, :onSite, :version)
            """;

    private final JdbcClient jdbc;

    public JdbcMatchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Match m) {
        jdbc.sql(INSERT)
                .param("id", m.id())
                .param("kind", m.kind())
                .param("status", m.status())
                .param("visibility", m.visibility())
                .param("openSpots", m.openSpots())
                .param("ownerId", m.ownerId())
                .param("payerId", m.payerId())
                .param("venueId", m.venueId())
                .param("slotId", m.slotId())
                .param("startsAt", m.startsAt().atOffset(ZoneOffset.UTC))
                .param("total", m.totalAmountCents())
                .param("currency", m.currency())
                .param("onSite", m.acceptsOnSitePayment())
                .param("version", m.version())
                .update();
    }
}
