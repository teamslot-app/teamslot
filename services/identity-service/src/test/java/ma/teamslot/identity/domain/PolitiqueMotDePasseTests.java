package ma.teamslot.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** SCRUM-132 — règles du mot de passe, sans Spring ni base de données. */
class PolitiqueMotDePasseTests {

    static final String TEL = "+212612345678";

    @Test
    void motDePasseSolide_aucuneViolation() {
        assertThat(PolitiqueMotDePasse.violations("Terrain-Synthe-2026", "aya@exemple.ma", "Aya", TEL)).isEmpty();
    }

    @Test
    void tropCourt() {
        assertThat(PolitiqueMotDePasse.violations("Court-12", null, "Aya", TEL)).contains("au moins 12 caractères");
    }

    @Test
    void tropLong() {
        assertThat(PolitiqueMotDePasse.violations("Aa1-" + "x".repeat(130), null, "Aya", TEL))
                .contains("au plus 128 caractères");
    }

    @Test
    void deuxFamillesSeulement_refuse() {
        assertThat(PolitiqueMotDePasse.violations("toutenminuscules2026", null, "Aya", TEL))
                .contains("au moins 3 types parmi minuscules, majuscules, chiffres et symboles");
    }

    @Test
    void troisFamilles_suffisent() {
        assertThat(PolitiqueMotDePasse.familles("terrain-synthe-2026")).isEqualTo(3);
        assertThat(PolitiqueMotDePasse.violations("terrain-synthe-2026", null, "Aya", TEL)).isEmpty();
    }

    @Test
    void contientLeNom_sansTenirCompteDesMajuscules() {
        assertThat(PolitiqueMotDePasse.violations("Yassir-Gardien-2026", null, "yassir", TEL))
                .contains("ne pas contenir le nom affiché");
    }

    @Test
    void contientLaPartieLocaleDeLEmail() {
        assertThat(PolitiqueMotDePasse.violations("Laila.Dev-Match-9", "laila.dev@exemple.ma", "L", TEL))
                .contains("ne pas contenir l'adresse e-mail");
    }

    @Test
    void contientLesChiffresDuNumero() {
        assertThat(PolitiqueMotDePasse.violations("Match-345678-Soir", null, "Aya", TEL))
                .contains("ne pas contenir le numéro de téléphone");
    }

    @Test
    void nomTresCourt_ignore() {
        // un nom de 2 lettres se retrouve par hasard dans beaucoup de mots de passe : on ne le cherche pas
        assertThat(PolitiqueMotDePasse.violations("Terrain-Synthe-2026", null, "Te", TEL)).isEmpty();
    }
}
