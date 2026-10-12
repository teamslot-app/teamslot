package ma.teamslot.venue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Horloge fixe : les tests ne dépendent plus de la date du jour. */
@TestConfiguration
public class TestClockConfig {

  public static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

  @Bean
  @Primary
  Clock fixedClock() {
    return Clock.fixed(NOW, ZoneOffset.UTC);
  }
}
