package ma.teamslot.venue.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class PriceCalculatorTest {

  private final PriceCalculator calculator = new PriceCalculator();

  @Test
  void priceWithoutEquipmentIsTheVenuePrice() {
    Money total = calculator.total(new Money(5000, "MAD"), List.of());
    assertThat(total).isEqualTo(new Money(5000, "MAD"));
  }

  @Test
  void priceWithEquipmentAddsTheSupplements() {
    Money total =
        calculator.total(
            new Money(5000, "MAD"), List.of(new Money(500, "MAD"), new Money(200, "MAD")));
    assertThat(total.amount()).isEqualTo(5700);
  }

  @Test
  void breakdownSeparatesBaseAndEquipment() {
    PriceCalculator.Breakdown breakdown =
        calculator.breakdown(
            new Money(15000, "MAD"), List.of(new Money(2000, "MAD"), new Money(1000, "MAD")));
    assertThat(breakdown.equipmentTotal()).isEqualTo(new Money(3000, "MAD"));
    assertThat(breakdown.total()).isEqualTo(new Money(18000, "MAD"));
  }

  @Test
  void differentCurrenciesAreRejected() {
    assertThatThrownBy(
            () -> calculator.total(new Money(5000, "MAD"), List.of(new Money(500, "EUR"))))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
