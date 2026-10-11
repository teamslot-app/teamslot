package ma.teamslot.venue.domain;

import java.util.Collection;

/** Seul endroit où le prix est calculé (réutilisé par la réservation, SCRUM-93). */
public final class PriceCalculator {

  public record Breakdown(Money base, Money equipmentTotal, Money total) {}

  public Breakdown breakdown(Money base, Collection<Money> equipmentSupplements) {
    Money equipmentTotal = new Money(0, base.currency());
    for (Money supplement : equipmentSupplements) {
      equipmentTotal = equipmentTotal.plus(supplement);
    }
    return new Breakdown(base, equipmentTotal, base.plus(equipmentTotal));
  }

  public Money total(Money base, Collection<Money> equipmentSupplements) {
    return breakdown(base, equipmentSupplements).total();
  }
}
