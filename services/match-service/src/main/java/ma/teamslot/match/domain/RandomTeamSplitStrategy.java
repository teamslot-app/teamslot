package ma.teamslot.match.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Tirage au sort (SCRUM-61). Le générateur est injecté : un test fixe la graine, la production
 * donne un SecureRandom. Avec un nombre impair, le côté A a le joueur de plus.
 */
public final class RandomTeamSplitStrategy implements TeamSplitStrategy {

    public static final String NAME = "random";

    private final RandomGenerator random;

    public RandomTeamSplitStrategy(RandomGenerator random) {
        this.random = random;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public TeamSplit split(List<Participant> players) {
        if (players.size() < 2) {
            throw new IllegalArgumentException("Il faut au moins deux joueurs pour former deux côtés.");
        }
        List<Participant> melange = new ArrayList<>(players);
        Collections.shuffle(melange, random);
        int tailleA = (melange.size() + 1) / 2;
        return new TeamSplit(melange.subList(0, tailleA), melange.subList(tailleA, melange.size()));
    }
}
