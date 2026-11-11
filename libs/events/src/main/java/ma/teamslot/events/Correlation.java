package ma.teamslot.events;

import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * Identifiant de corrélation dans les événements (SCRUM-89).
 * À l'écriture, EventEnvelope.of le lit dans le MDC (rempli par CorrelationIdFilter pour chaque requête HTTP) ;
 * à la lecture, le consommateur le remet dans le MDC pendant le traitement, puis le retire.
 * Une requête se suit ainsi d'un service à l'autre, à travers l'outbox et Kafka.
 */
public final class Correlation {

    /** Même clé que CorrelationIdFilter.MDC_KEY dans chaque service. */
    public static final String MDC_KEY = "correlationId";

    /** UUID uniquement : un événement mal formé ne doit pas pouvoir injecter du texte dans les logs. */
    private static final Pattern UUID_FORMAT =
            Pattern.compile("^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$");

    private Correlation() {
    }

    /** @return la valeur si c'est un UUID, sinon null (valeur ignorée, jamais rejetée). */
    public static String valide(String valeur) {
        return valeur != null && UUID_FORMAT.matcher(valeur).matches() ? valeur : null;
    }

    /** Identifiant de la requête en cours (MDC), ou null hors requête ou s'il n'est pas un UUID. */
    public static String courante() {
        return valide(MDC.get(MDC_KEY));
    }

    /**
     * Exécute le traitement avec cet identifiant dans le MDC, puis remet la valeur d'avant (ou rien).
     * Un identifiant absent ou invalide ne change pas le MDC : le traitement s'exécute quand même.
     */
    public static void pendant(String correlationId, Runnable traitement) {
        String valeur = valide(correlationId);
        if (valeur == null) {
            traitement.run();
            return;
        }
        String avant = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, valeur);
        try {
            traitement.run();
        } finally {
            if (avant == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, avant);
            }
        }
    }
}
