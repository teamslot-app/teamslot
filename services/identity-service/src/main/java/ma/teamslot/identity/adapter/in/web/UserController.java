package ma.teamslot.identity.adapter.in.web;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import ma.teamslot.identity.adapter.out.persistence.UserEntity;
import ma.teamslot.identity.application.AuthErrors;
import ma.teamslot.identity.application.ProfilService;
import ma.teamslot.identity.application.RechercheJoueursService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** SCRUM-95 et SCRUM-82 — contrat docs/api/identity.yaml : getMe, updateMe, searchUsers. Jeton exigé (SecuriteConfig). */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    /** Seul champ modifiable (UpdateUserRequest, additionalProperties: false). */
    private static final Set<String> MODIFIABLES = Set.of("displayName");

    private final ProfilService profil;
    private final RechercheJoueursService recherche;

    public UserController(ProfilService profil, RechercheJoueursService recherche) {
        this.profil = profil;
        this.recherche = recherche;
    }

    /**
     * SCRUM-82 : chercher un joueur par le début de son surnom, pour l'inviter. Réponse minimale (UserSummary) :
     * identifiant et surnom seulement, jamais le téléphone ni l'e-mail. Limitée à 30 recherches par minute et par compte.
     */
    @GetMapping
    public ResponseEntity<List<UserSummary>> chercher(@RequestParam(name = "displayName", required = false) String displayName,
                                                      @AuthenticationPrincipal Jwt jeton) {
        List<UserSummary> trouves = recherche.chercher(appelant(jeton), displayName).stream()
                .map(UserSummary::from)
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(trouves);
    }

    /** Schéma UserSummary du contrat : deux champs, rien d'autre ne sort. */
    public record UserSummary(UUID id, String displayName) {
        static UserSummary from(UserEntity user) {
            return new UserSummary(user.getId(), user.getDisplayName());
        }
    }

    @GetMapping("/me")
    public ResponseEntity<AuthController.UserResponse> monProfil(@AuthenticationPrincipal Jwt jeton) {
        return reponse(AuthController.UserResponse.from(profil.monProfil(appelant(jeton))));
    }

    /**
     * Le corps est lu champ par champ : un champ inconnu (platformRole, phone, email…) donne 400 et rien n'est
     * modifié. Protection contre l'affectation de masse, à l'inverse de l'inscription qui ignore ces champs.
     */
    @PatchMapping("/me")
    public ResponseEntity<AuthController.UserResponse> modifier(@RequestBody Map<String, Object> corps,
                                                                @AuthenticationPrincipal Jwt jeton) {
        if (corps == null || !MODIFIABLES.containsAll(corps.keySet())) {
            throw new AuthErrors.InvalidProfile("Seul le surnom (displayName) peut être modifié.");
        }
        if (!(corps.get("displayName") instanceof String surnom)) {
            throw new AuthErrors.InvalidProfile("Le surnom (displayName) est obligatoire.");
        }
        return reponse(AuthController.UserResponse.from(profil.changerSurnom(appelant(jeton), surnom)));
    }

    /** Données personnelles : jamais mises en cache. */
    private static ResponseEntity<AuthController.UserResponse> reponse(AuthController.UserResponse corps) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(corps);
    }

    /** Claim sub du jeton déjà vérifié par Spring Security. */
    private static UUID appelant(Jwt jeton) {
        try {
            return UUID.fromString(jeton.getSubject());
        } catch (RuntimeException e) {
            throw new AuthErrors.AccountNotFound();
        }
    }
}
