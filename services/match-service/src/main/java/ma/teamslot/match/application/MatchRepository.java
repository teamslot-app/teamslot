package ma.teamslot.match.application;

import ma.teamslot.match.domain.Match;

public interface MatchRepository {
    void save(Match match);
}
