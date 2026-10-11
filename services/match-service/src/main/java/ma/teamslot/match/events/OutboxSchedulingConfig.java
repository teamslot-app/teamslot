package ma.teamslot.match.events;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Active le relais planifié. Désactivable avec teamslot.outbox.relay.enabled=false (tests). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "teamslot.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
class OutboxSchedulingConfig {
}
