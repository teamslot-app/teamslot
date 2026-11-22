package ma.teamslot.venue.application;

import java.util.List;

public record VenueListPage(List<VenueListItem> content, int page, int size, long totalElements) {}
