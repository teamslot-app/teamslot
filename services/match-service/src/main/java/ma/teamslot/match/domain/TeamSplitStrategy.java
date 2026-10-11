package ma.teamslot.match.domain;

import java.util.List;

/**
 * Point d'extension de la répartition (docs/api/match.yaml, splitTeams) :
 * « random » aujourd'hui, « balanced » plus tard, sans changer le contrat.
 */
public interface TeamSplitStrategy {

    /** Valeur du paramètre strategy du contrat. */
    String name();

    /** Répartit tous les joueurs : chacun dans un seul côté, effectifs à un joueur près. */
    TeamSplit split(List<Participant> players);
}
