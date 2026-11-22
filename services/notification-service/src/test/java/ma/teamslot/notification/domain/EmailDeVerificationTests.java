package ma.teamslot.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** SCRUM-134 : contenu de l'e-mail de vérification, sans dépendre d'aucun fuseau horaire. */
class EmailDeVerificationTests {

    private static final Instant ENVOI = Instant.parse("2026-11-15T01:06:00Z");

    private static EmailDeVerification email(Instant expire) {
        return new EmailDeVerification(UUID.randomUUID(), "sara.test@example.com", "Sara", "608397", expire);
    }

    @Test
    void la_duree_restante_ne_depend_pas_du_fuseau() {
        EmailDeVerification email = email(ENVOI.plusSeconds(15 * 60));

        assertThat(email.dureeRestante(ENVOI)).isEqualTo("15 minutes");
        assertThat(email.dureeRestante(ENVOI.plusSeconds(14 * 60 + 30))).isEqualTo("1 minute");
        assertThat(email.texte(ENVOI)).contains("608397").contains("valable 15 minutes").doesNotContain("heure");
        assertThat(email.html(ENVOI)).contains("<strong>15 minutes</strong>");
    }

    @Test
    void un_code_qui_n_a_pas_six_chiffres_est_refuse() {
        // Sonar S5778 : un seul appel qui peut lever une exception dans le lambda (l'identifiant est créé avant)
        UUID compte = UUID.randomUUID();
        assertThatThrownBy(() -> new EmailDeVerification(compte, "a@example.com", "A", "12ab56", ENVOI))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toString_ne_montre_ni_le_code_ni_l_adresse() {
        assertThat(email(ENVOI).toString()).doesNotContain("608397").doesNotContain("sara.test@example.com");
    }
}
