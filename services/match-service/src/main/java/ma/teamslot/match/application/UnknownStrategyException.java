package ma.teamslot.match.application;

import java.util.Set;
import java.util.TreeSet;

/** SCRUM-61 : la stratégie demandée n'existe pas (400). Le message n'écrit pas la valeur reçue. */
public class UnknownStrategyException extends RuntimeException {
    public UnknownStrategyException(Set<String> disponibles) {
        super("Stratégie inconnue. Valeurs possibles : " + String.join(", ", new TreeSet<>(disponibles)) + ".");
    }
}
