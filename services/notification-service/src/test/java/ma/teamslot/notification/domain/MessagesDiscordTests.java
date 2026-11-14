package ma.teamslot.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** SCRUM-81 : les messages du salon partagé ne contiennent que le début de l'identifiant du match. */
class MessagesDiscordTests {

    private static final UUID MATCH = UUID.fromString("a1b2c3d4-0000-4000-8000-000000000001");

    @Test
    void messages_courts_sans_identifiant_complet() {
        assertThat(MessagesDiscord.participantAjoute(MATCH)).contains("a1b2c3d4").doesNotContain(MATCH.toString());
        assertThat(MessagesDiscord.demandeAcceptee(MATCH)).contains("a1b2c3d4").doesNotContain(MATCH.toString());
    }

    @Test
    void confirmation_accorde_singulier_et_pluriel() {
        assertThat(MessagesDiscord.matchConfirme(MATCH, 1)).endsWith("1 joueur prévenu.");
        assertThat(MessagesDiscord.matchConfirme(MATCH, 3)).endsWith("3 joueurs prévenus.");
    }
}
