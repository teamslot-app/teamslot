package ma.teamslot.venue.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

  /** Horloge injectable : les tests la remplacent par une horloge fixe. */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
