package ma.teamslot.payment.adapter.out.persistence;

import ma.teamslot.payment.application.PaymentRepository;
import ma.teamslot.payment.domain.Payment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPaymentRepository implements PaymentRepository {

    private static final String INSERT_SI_ABSENT = """
            INSERT INTO payments (id, match_id, payer_id, total_amount_cents, currency,
                                  accepts_on_site_payment, status)
            VALUES (:id, :matchId, :payerId, :total, :currency, :onSite, :status)
            ON CONFLICT (match_id) DO NOTHING
            """;

    private final JdbcClient jdbc;

    public JdbcPaymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean creerSiAbsent(Payment p) {
        return jdbc.sql(INSERT_SI_ABSENT)
                .param("id", p.id())
                .param("matchId", p.matchId())
                .param("payerId", p.payerId())
                .param("total", p.totalAmountCents())
                .param("currency", p.currency())
                .param("onSite", p.acceptsOnSitePayment())
                .param("status", p.status().name())
                .update() == 1;
    }
}
