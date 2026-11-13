package ma.teamslot.identity.domain;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * SCRUM-132 — cohérence entre le pays choisi (drapeau) et le numéro au format international.
 * Pas de bibliothèque externe : on vérifie l'indicatif des pays proposés par le front, et pour le Maroc
 * la forme exacte d'un mobile ou fixe (+212 puis 5, 6 ou 7, puis 8 chiffres). Un pays absent de la
 * liste est accepté tant que le numéro est au format E.164 (déjà vérifié par le contrôleur).
 */
public final class IndicatifsPays {

    private static final Map<String, String> INDICATIFS = Map.ofEntries(
            Map.entry("MA", "212"), Map.entry("DZ", "213"), Map.entry("TN", "216"), Map.entry("MR", "222"),
            Map.entry("SN", "221"), Map.entry("FR", "33"), Map.entry("ES", "34"), Map.entry("BE", "32"),
            Map.entry("IT", "39"), Map.entry("DE", "49"), Map.entry("NL", "31"), Map.entry("GB", "44"),
            Map.entry("PT", "351"), Map.entry("CH", "41"), Map.entry("US", "1"), Map.entry("CA", "1"),
            Map.entry("EG", "20"), Map.entry("SA", "966"), Map.entry("AE", "971"), Map.entry("QA", "974"));

    private static final Pattern MAROC = Pattern.compile("^\\+212[5-7][0-9]{8}$");

    private IndicatifsPays() {
    }

    /** Vrai si le numéro correspond au pays ; un pays null (ancien client) n'est pas vérifié. */
    public static boolean coherent(String pays, String telephone) {
        if (pays == null) {
            return true;
        }
        if ("MA".equals(pays)) {
            return MAROC.matcher(telephone).matches();
        }
        String indicatif = INDICATIFS.get(pays);
        return indicatif == null || telephone.startsWith("+" + indicatif);
    }
}
