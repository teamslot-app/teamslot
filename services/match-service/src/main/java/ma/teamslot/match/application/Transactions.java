package ma.teamslot.match.application;

import java.util.function.Supplier;

/**
 * Transaction de la base, rejouée si elle a échoué sur un conflit transitoire (deux écritures sur le même match
 * en même temps, SCRUM-79). Le travail est relu à chaque essai : le second voit l'état laissé par le premier.
 */
public interface Transactions {
    <T> T executer(Supplier<T> travail);
}
