package ma.teamslot.match.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Regles du participant (SCRUM-60), sans Spring ni base de donnees. */
class ParticipantTest {

    private final UUID matchId = UUID.randomUUID();

    @Test
    void un_compte_est_invite_et_doit_confirmer() {
        UUID ami = UUID.randomUUID();

        Participant p = Participant.compte(matchId, ami);

        assertThat(p.userId()).isEqualTo(ami);
        assertThat(p.guestName()).isNull();
        assertThat(p.status()).isEqualTo(ParticipantStatus.INVITED);
    }

    @Test
    void un_invite_sans_compte_est_confirme_et_son_surnom_nettoye() {
        Participant p = Participant.invite(matchId, "  Yassir   el  Amrani ");

        assertThat(p.userId()).isNull();
        assertThat(p.guestName()).isEqualTo("Yassir el Amrani");
        assertThat(p.status()).isEqualTo(ParticipantStatus.CONFIRMED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Zoé", "O'Neil-Jr.", "Équipe 10", "Amine 2"})
    void surnoms_acceptes(String surnom) {
        assertThat(Participant.invite(matchId, surnom).guestName()).isEqualTo(surnom);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "yassir@mail.ma", "0612345678", "06 12 34 56 78",
            "<script>", "x;DROP TABLE"})
    void surnoms_refuses_vides_e_mail_numero_ou_caracteres_speciaux(String surnom) {
        assertThatThrownBy(() -> Participant.invite(matchId, surnom))
                .isInstanceOf(InvalidParticipantException.class);
    }

    @Test
    void surnom_de_plus_de_40_caracteres_refuse() {
        String trop = "a".repeat(Participant.SURNOM_MAX + 1);

        assertThatThrownBy(() -> Participant.invite(matchId, trop))
                .isInstanceOf(InvalidParticipantException.class);
    }

    @Test
    void jamais_compte_et_surnom_en_meme_temps() {
        UUID id = UUID.randomUUID();
        UUID ami = UUID.randomUUID();

        assertThatThrownBy(() -> new Participant(id, matchId, ami, "Yassir", ParticipantStatus.INVITED))
                .isInstanceOf(InvalidParticipantException.class);
        assertThatThrownBy(() -> new Participant(id, matchId, null, null, ParticipantStatus.INVITED))
                .isInstanceOf(InvalidParticipantException.class);
    }
}
