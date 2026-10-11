package ma.teamslot.match.application;

import java.security.SecureRandom;
import ma.teamslot.match.domain.RandomTeamSplitStrategy;
import ma.teamslot.match.domain.TeamSplitStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Stratégies de répartition disponibles. « balanced » viendra plus tard : un bean de plus, sans changer le contrat. */
@Configuration
class TeamSplitConfig {

    @Bean
    TeamSplitStrategy randomTeamSplitStrategy() {
        return new RandomTeamSplitStrategy(new SecureRandom());
    }
}
