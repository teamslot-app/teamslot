package ma.teamslot.match.domain;

import java.util.List;

/** Résultat d'une répartition (SCRUM-61) : deux côtés, un joueur n'est jamais des deux. */
public record TeamSplit(List<Participant> sideA, List<Participant> sideB) {

    public TeamSplit {
        sideA = List.copyOf(sideA);
        sideB = List.copyOf(sideB);
    }
}
