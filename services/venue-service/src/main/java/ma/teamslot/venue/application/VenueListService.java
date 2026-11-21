package ma.teamslot.venue.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import ma.teamslot.venue.domain.Money;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SCRUM-138 : liste paginée des terrains. Sans position : tri par nom puis id.
 * Avec position : tri par distance puis id, rayon appliqué. Toutes les valeurs de la requête
 * passent en paramètres (S8) ; le SQL concaténé ne contient que des constantes.
 */
@Service
public class VenueListService {

  static final int MAX_SIZE = 50;
  private static final int RAYON_TERRE_METRES = 6_371_000;

  private static final String COLONNES =
      "v.id, v.name, v.latitude, v.longitude, v.price_per_hour_cents, v.currency,"
          + " v.accepts_on_site_payment";

  /** Haversine ; trois paramètres : latitude, latitude, longitude. */
  private static final String DISTANCE =
      RAYON_TERRE_METRES
          + " * 2 * asin(sqrt(least(1.0, power(sin(radians((v.latitude - ?) / 2)), 2)"
          + " + cos(radians(?)) * cos(radians(v.latitude))"
          + " * power(sin(radians((v.longitude - ?) / 2)), 2))))";

  private final JdbcTemplate jdbc;

  public VenueListService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public VenueListPage list(Double lat, Double lon, int radius, int page, int size) {
    if ((lat == null) != (lon == null)) {
      throw new InvalidRequestException("lat et lon vont ensemble : les deux ou aucun.");
    }
    if (lat != null) {
      if (!Double.isFinite(lat) || lat < -90 || lat > 90 || !Double.isFinite(lon) || lon < -180 || lon > 180) {
        throw new InvalidRequestException("Position invalide.");
      }
      if (radius < 1) {
        throw new InvalidRequestException("Le rayon doit être au moins 1 mètre.");
      }
    }
    if (page < 0) {
      throw new InvalidRequestException("page doit être positif ou nul.");
    }
    if (size < 1 || size > MAX_SIZE) {
      throw new InvalidRequestException("size doit être compris entre 1 et " + MAX_SIZE + ".");
    }
    long offset = (long) page * size;

    List<VenueListItem> items;
    long total;
    if (lat == null) {
      items =
          jdbc.query(
              "select " + COLONNES + ", cast(null as double precision) as dist from venue v"
                  + " order by v.name, v.id limit ? offset ?",
              MAPPER, size, offset);
      total = jdbc.queryForObject("select count(*) from venue", Long.class);
    } else {
      items =
          jdbc.query(
              "select t.* from (select " + COLONNES + ", " + DISTANCE + " as dist from venue v) t"
                  + " where t.dist <= ? order by t.dist, t.id limit ? offset ?",
              MAPPER, lat, lat, lon, radius, size, offset);
      total =
          jdbc.queryForObject(
              "select count(*) from (select " + DISTANCE + " as dist from venue v) t where t.dist <= ?",
              Long.class, lat, lat, lon, radius);
    }
    return new VenueListPage(avecEquipements(items), page, size, total);
  }

  private List<VenueListItem> avecEquipements(List<VenueListItem> items) {
    if (items.isEmpty()) {
      return items;
    }
    List<UUID> ids = items.stream().map(VenueListItem::id).toList();
    String marques = String.join(",", Collections.nCopies(ids.size(), "?"));
    Map<UUID, List<EquipmentLigne>> parTerrain =
        jdbc
            .query(
                "select id, venue_id, name, price_cents from equipment where venue_id in ("
                    + marques + ") order by name, id",
                (rs, n) ->
                    new EquipmentLigne(
                        rs.getObject("venue_id", UUID.class),
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getLong("price_cents")),
                ids.toArray())
            .stream()
            .collect(Collectors.groupingBy(EquipmentLigne::venueId));
    List<VenueListItem> resultat = new ArrayList<>();
    for (VenueListItem item : items) {
      List<EquipmentView> equipements =
          parTerrain.getOrDefault(item.id(), List.of()).stream()
              .map(e -> new EquipmentView(e.id(), e.name(), new Money(e.priceCents(), item.pricePerHour().currency())))
              .toList();
      resultat.add(
          new VenueListItem(
              item.id(),
              item.name(),
              item.latitude(),
              item.longitude(),
              item.pricePerHour(),
              item.acceptsOnSitePayment(),
              equipements,
              item.distanceMeters()));
    }
    return resultat;
  }

  private record EquipmentLigne(UUID venueId, UUID id, String name, long priceCents) {}

  private static final RowMapper<VenueListItem> MAPPER =
      (rs, n) -> {
        double distance = rs.getDouble("dist");
        Integer metres = rs.wasNull() ? null : (int) Math.round(distance);
        return new VenueListItem(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getDouble("latitude"),
            rs.getDouble("longitude"),
            new Money(rs.getLong("price_per_hour_cents"), rs.getString("currency")),
            rs.getBoolean("accepts_on_site_payment"),
            List.of(),
            metres);
      };
}
