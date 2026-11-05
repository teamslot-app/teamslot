package ma.teamslot.venue.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import ma.teamslot.venue.application.CreateVenueCommand;
import ma.teamslot.venue.application.CreateVenueCommand.EquipmentCommand;

/** Corps de POST /api/v1/venues (docs/api/venue.yaml). Montants en centimes avec la devise. */
public record CreateVenueRequest(
    @NotBlank @Size(max = 120) String name,
    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
    @NotNull @Valid MoneyInput pricePerHour,
    Boolean acceptsOnSitePayment,
    @Size(max = 20) List<@Valid @NotNull EquipmentInput> equipment) {

  public record MoneyInput(
      @NotNull @Min(0) Long amount, @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {}

  public record EquipmentInput(
      @NotBlank @Size(max = 60) String name, @NotNull @Valid MoneyInput extraPrice) {}

  CreateVenueCommand toCommand() {
    List<EquipmentCommand> equipments =
        equipment == null
            ? List.of()
            : equipment.stream()
                .map(e -> new EquipmentCommand(e.name(), e.extraPrice().amount(), e.extraPrice().currency()))
                .toList();
    return new CreateVenueCommand(
        name,
        latitude,
        longitude,
        pricePerHour.amount(),
        pricePerHour.currency(),
        Boolean.TRUE.equals(acceptsOnSitePayment),
        equipments);
  }
}
