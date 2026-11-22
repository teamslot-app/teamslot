package ma.teamslot.identity.application;

import java.util.List;
import java.util.UUID;

import ma.teamslot.identity.adapter.out.persistence.UserEntity;
import ma.teamslot.identity.adapter.out.persistence.UserRepository;
import org.springframework.stereotype.Service;

/**
 * SCRUM-82 : chercher un joueur par le début de son surnom. Le compte qui cherche vient de la claim sub du jeton
 * (UserController) : il sert à la limite de débit et n'apparaît jamais dans ses propres résultats.
 */
@Service
public class RechercheJoueursService {

    static final int TEXTE_MIN = 3;
    static final int TEXTE_MAX = 40;

    private final UserRepository users;
    private final LimiteRecherche limite;

    public RechercheJoueursService(UserRepository users, LimiteRecherche limite) {
        this.users = users;
        this.limite = limite;
    }

    /** Chaque appel compte, même refusé : un texte invalide ne permet pas de contourner la limite. */
    public List<UserEntity> chercher(UUID moi, String texte) {
        long attente = limite.attendreAvantRecherche(moi);
        if (attente > 0) {
            throw new AuthErrors.TooManySearches(attente);
        }
        String propre = texte == null ? "" : texte.strip();
        if (propre.length() < TEXTE_MIN || propre.length() > TEXTE_MAX
                || propre.chars().anyMatch(Character::isISOControl)) {
            throw new AuthErrors.InvalidSearch(
                    "Le surnom recherché doit faire de " + TEXTE_MIN + " à " + TEXTE_MAX + " caractères.");
        }
        return users.findTop20ByDisplayNameStartingWithAndIdNotOrderByDisplayNameAsc(propre, moi);
    }
}
