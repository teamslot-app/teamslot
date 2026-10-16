package ma.teamslot.payment;

import java.util.function.BooleanSupplier;

/** Attente des effets asynchrones (consommateurs Kafka) dans les tests. */
final class Attente {

    private Attente() {
    }

    /** Attend qu'une condition soit vraie, 30 secondes au maximum. */
    static void jusqua(BooleanSupplier condition) throws InterruptedException {
        long fin = System.currentTimeMillis() + 30_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < fin) {
            Thread.sleep(200);
        }
    }
}
