package ma.teamslot.notification;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/** Attente des effets asynchrones (consommateurs Kafka) dans les tests. */
final class Attente {

    private Attente() {
    }

    /**
     * Attend qu'une condition soit vraie, 30 secondes au maximum.
     * Si elle ne l'est jamais, le test échoue ici avec un message clair (ConditionTimeoutException).
     */
    static void jusqua(BooleanSupplier condition) {
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .until(condition::getAsBoolean);
    }
}
