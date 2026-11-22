package ma.teamslot.identity.adapter.in.web;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import ma.teamslot.identity.application.AuthErrors;
import ma.teamslot.identity.application.ProfilService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** SCRUM-95 — contrat docs/api/identity.yaml : getMe et updateMe. Jeton exigé (SecuriteConfig). */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    /** Seul champ modifiable (UpdateUserRequest, additionalProperties: false). */
    private static final Set<String> MODIFIABLES = Set.of("displayName");

    private final ProfilService profil;

    public UserController(ProfilService profil) {
        this.profil = profil;
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
