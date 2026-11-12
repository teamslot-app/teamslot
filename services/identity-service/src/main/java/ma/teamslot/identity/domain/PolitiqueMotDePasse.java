package ma.teamslot.identity.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SCRUM-132 — règles du mot de passe, vérifiées par le serveur (le front ne fait qu'aider l'utilisateur).
 * Classe pure : aucune dépendance à Spring, testée directement.
 *
 * Règles : 12 à 128 caractères ; au moins 3 familles parmi minuscules, majuscules, chiffres et symboles ;
 * ne contient ni le nom affiché, ni la partie locale de l'e-mail, ni les 6 derniers chiffres du numéro.
 */
public final class PolitiqueMotDePasse {

    public static final int LONGUEUR_MIN = 12;
    public static final int LONGUEUR_MAX = 128;
    static final int FAMILLES_MIN = 3;
    /** En dessous de cette longueur, un nom ou un e-mail est trop court pour être cherché dans le mot de passe. */
    private static final int MORCEAU_MIN = 3;

    private PolitiqueMotDePasse() {
    }

    /** Liste des règles non respectées, en français ; vide si le mot de passe est accepté. */
    public static List<String> violations(String motDePasse, String email, String nomAffiche, String telephone) {
        List<String> violations = new ArrayList<>();
        if (motDePasse == null || motDePasse.length() < LONGUEUR_MIN) {
            violations.add("au moins " + LONGUEUR_MIN + " caractères");
        }
        if (motDePasse != null && motDePasse.length() > LONGUEUR_MAX) {
            violations.add("au plus " + LONGUEUR_MAX + " caractères");
        }
        if (motDePasse == null) {
            return violations;
        }
        if (familles(motDePasse) < FAMILLES_MIN) {
            violations.add("au moins 3 types parmi minuscules, majuscules, chiffres et symboles");
        }
        String bas = motDePasse.toLowerCase(Locale.ROOT);
        if (contient(bas, nomAffiche)) {
            violations.add("ne pas contenir le nom affiché");
        }
        if (email != null && email.contains("@") && contient(bas, email.substring(0, email.indexOf('@')))) {
            violations.add("ne pas contenir l'adresse e-mail");
        }
        if (telephone != null) {
            String chiffres = telephone.replaceAll("\\D", "");
            if (chiffres.length() >= 6 && motDePasse.contains(chiffres.substring(chiffres.length() - 6))) {
                violations.add("ne pas contenir le numéro de téléphone");
            }
        }
        return violations;
    }

    static int familles(String motDePasse) {
        boolean minuscule = false;
        boolean majuscule = false;
        boolean chiffre = false;
        boolean symbole = false;
        for (int i = 0; i < motDePasse.length(); i++) {
            char c = motDePasse.charAt(i);
            if (Character.isLowerCase(c)) {
                minuscule = true;
            } else if (Character.isUpperCase(c)) {
                majuscule = true;
            } else if (Character.isDigit(c)) {
                chiffre = true;
            } else {
                symbole = true;
            }
        }
        return (minuscule ? 1 : 0) + (majuscule ? 1 : 0) + (chiffre ? 1 : 0) + (symbole ? 1 : 0);
    }

    private static boolean contient(String motDePasseEnMinuscules, String morceau) {
        if (morceau == null) {
            return false;
        }
        String m = morceau.strip().toLowerCase(Locale.ROOT);
        return m.length() >= MORCEAU_MIN && motDePasseEnMinuscules.contains(m);
    }
}
