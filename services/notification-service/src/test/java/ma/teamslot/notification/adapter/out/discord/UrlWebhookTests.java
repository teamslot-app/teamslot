package ma.teamslot.notification.adapter.out.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** SCRUM-81 : seule une URL de webhook Discord en HTTPS est acceptée ; le message d'erreur ne contient jamais l'URL. */
class UrlWebhookTests {

    @Test
    void url_absente_desactive_l_envoi() {
        assertThat(UrlWebhook.valider("", false)).isNull();
        assertThat(UrlWebhook.valider(null, false)).isNull();
    }

    @Test
    void url_de_webhook_discord_acceptee() {
        assertThat(UrlWebhook.valider("https://discord.com/api/webhooks/123456/abc_DEF-789", false)).isNotNull();
    }

    @Test
    void autre_adresse_refusee_sans_reveler_l_url() {
        String[] refusees = {
            "http://discord.com/api/webhooks/123456/abc",          // pas HTTPS
            "https://exemple.org/api/webhooks/123456/abc",          // pas Discord
            "https://discord.com.exemple.org/api/webhooks/1/abc",   // faux domaine
            "https://discord.com/api/autre/123456/abc",             // pas un webhook
            "http://localhost:8080/api/webhooks/1/abc"              // localhost interdit hors tests
        };
        for (String url : refusees) {
            assertThatThrownBy(() -> UrlWebhook.valider(url, false))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageNotContaining("abc");
        }
    }

    @Test
    void localhost_accepte_seulement_si_autorise() {
        assertThat(UrlWebhook.valider("http://localhost:8080/api/webhooks/1/abc", true)).isNotNull();
    }
}
