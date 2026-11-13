package ma.teamslot.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** SCRUM-132 — le numéro doit correspondre au drapeau choisi. */
class IndicatifsPaysTests {

    @Test
    void maroc_mobileEtFixe() {
        assertThat(IndicatifsPays.coherent("MA", "+212612345678")).isTrue();
        assertThat(IndicatifsPays.coherent("MA", "+212712345678")).isTrue();
        assertThat(IndicatifsPays.coherent("MA", "+212522123456")).isTrue();
    }

    @Test
    void maroc_mauvaiseForme() {
        assertThat(IndicatifsPays.coherent("MA", "+21261234567")).isFalse();
        assertThat(IndicatifsPays.coherent("MA", "+212812345678")).isFalse();
        assertThat(IndicatifsPays.coherent("MA", "+33612345678")).isFalse();
    }

    @Test
    void autresPays_parIndicatif() {
        assertThat(IndicatifsPays.coherent("FR", "+33612345678")).isTrue();
        assertThat(IndicatifsPays.coherent("FR", "+212612345678")).isFalse();
    }

    @Test
    void paysAbsent_ouInconnu_accepte() {
        assertThat(IndicatifsPays.coherent(null, "+33612345678")).isTrue();
        assertThat(IndicatifsPays.coherent("JP", "+81312345678")).isTrue();
    }
}
