package ma.teamslot.match.application;

import java.util.List;
import java.util.UUID;
import ma.teamslot.match.domain.Participant;
import ma.teamslot.match.domain.TeamSplit;

/** Champ data de match.teams_split : exactement les champs de docs/events.md (user:<id> ou guest:<surnom>). */
public record TeamSplitData(UUID matchId, String strategy, List<String> sideA, List<String> sideB) {

    public static TeamSplitData de(UUID matchId, String strategy, TeamSplit split) {
        return new TeamSplitData(matchId, strategy, jetons(split.sideA()), jetons(split.sideB()));
    }

    private static List<String> jetons(List<Participant> joueurs) {
        return joueurs.stream().map(TeamSplitData::jeton).toList();
    }

    private static String jeton(Participant p) {
        return p.userId() != null ? "user:" + p.userId() : "guest:" + p.guestName();
    }
}
