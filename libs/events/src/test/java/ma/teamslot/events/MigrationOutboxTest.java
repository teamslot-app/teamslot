package ma.teamslot.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/**
 * Garde-fou de l'ADR-015 : V2__outbox.sql est déjà appliquée en staging (match, payment, venue, modèle).
 * Si ce test échoue, on ne change PAS l'empreinte : on remet le fichier d'origine et on ajoute une migration V3.
 */
class MigrationOutboxTest {

    /** SHA-256 du fichier sans les \r : même résultat sous Windows et en CI. */
    private static final String EMPREINTE_V2 = "d036e92693b750c9db389b0f625e717e40ab5dd40e89419eead0500592d8a8f1";

    @Test
    void la_migration_v2_outbox_n_a_pas_change() throws IOException, NoSuchAlgorithmException {
        byte[] contenu;
        try (InputStream fichier = getClass().getResourceAsStream("/db/migration/V2__outbox.sql")) {
            assertThat(fichier).as("V2__outbox.sql doit être fournie par libs/events").isNotNull();
            contenu = fichier.readAllBytes();
        }
        String sansRetourChariot = new String(contenu, StandardCharsets.UTF_8).replace("\r", "");
        byte[] empreinte = MessageDigest.getInstance("SHA-256")
                .digest(sansRetourChariot.getBytes(StandardCharsets.UTF_8));

        assertThat(HexFormat.of().formatHex(empreinte))
                .as("V2__outbox.sql est déjà appliquée : ne pas la modifier, ajouter une migration V3")
                .isEqualTo(EMPREINTE_V2);
    }
}
