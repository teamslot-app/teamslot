package ma.teamslot.match.application;

/**
 * Nombre de places refusé (SCRUM-78) : moins de 1, ou joueurs inscrits + places > maxPlayers. Réponse 400.
 * placesPossibles est le maximum accepté pour ce match (0 si le match est déjà complet).
 */
public class InvalidOpenSpotsException extends RuntimeException {

    private final int placesPossibles;

    public InvalidOpenSpotsException(int placesPossibles) {
        super("Nombre de places refusé ; maximum pour ce match : " + placesPossibles);
        this.placesPossibles = placesPossibles;
    }

    public int placesPossibles() {
        return placesPossibles;
    }
}
