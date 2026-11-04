package ma.teamslot.venue.adapter.in.web;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Débuts (UTC) des créneaux d'1 h à publier. */
public record CreateSlotsRequest(@NotEmpty @Size(max = 100) List<@NotNull Instant> starts) {}
