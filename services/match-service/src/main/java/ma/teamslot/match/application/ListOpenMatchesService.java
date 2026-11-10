package ma.teamslot.match.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * SCRUM-118 (provisoire) : matchs d'un jour qui ont des places ouvertes. Seul moyen de trouver un match
 * à rejoindre tant que search-service n'existe pas (ADR-012) ; à retirer quand la carte existera.
 */
@Service
public class ListOpenMatchesService {

    /** Le jour demandé est celui des joueurs, à Marrakech (comme format.ts dans le front). */
    static final ZoneId FUSEAU = ZoneId.of("Africa/Casablanca");
    static final int LIMITE = 50;

    private final MatchRepository matches;
    private final Clock horloge;

    public ListOpenMatchesService(MatchRepository matches, Clock horloge) {
        this.matches = matches;
        this.horloge = horloge;
    }

    /** Matchs PENDING_PAYMENT ou CONFIRMED du jour, pas encore commencés, avec au moins une place, par heure. */
    public List<MatchOuvert> ouverts(LocalDate jour) {
        Instant debutDuJour = jour.atStartOfDay(FUSEAU).toInstant();
        Instant finDuJour = jour.plusDays(1).atStartOfDay(FUSEAU).toInstant();
        Instant maintenant = horloge.instant();
        if (!finDuJour.isAfter(maintenant)) {
            return List.of(); // jour passé : rien à rejoindre, inutile d'interroger la base
        }
        Instant depuis = debutDuJour.isAfter(maintenant) ? debutDuJour : maintenant;
        return matches.ouverts(depuis, finDuJour, LIMITE);
    }
}
