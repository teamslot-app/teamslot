package ma.teamslot.venue.domain;

/** Montant en centimes avec sa devise (contrat venue.yaml : amount, currency). */
public record Money(long amount, String currency) {

  public Money {
    if (amount < 0) {
      throw new IllegalArgumentException("Le montant ne peut pas être négatif");
    }
    if (currency == null || currency.length() != 3) {
      throw new IllegalArgumentException("La devise doit avoir 3 lettres");
    }
  }

  public Money plus(Money other) {
    if (!currency.equals(other.currency)) {
      throw new IllegalArgumentException("Devises différentes");
    }
    return new Money(Math.addExact(amount, other.amount), currency);
  }
}
