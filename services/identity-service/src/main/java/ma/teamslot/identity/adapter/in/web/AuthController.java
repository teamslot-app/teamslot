package ma.teamslot.identity.adapter.in.web;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ma.teamslot.identity.adapter.out.persistence.UserEntity;
import ma.teamslot.identity.application.AuthService;
import ma.teamslot.identity.application.TokenPair;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** SCRUM-57 — contrat docs/api/identity.yaml : register, login, refresh. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(auth.register(request.phone(), request.password(), request.displayName()));
    }

    @PostMapping("/login")
    public TokenPair login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.phone(), request.password());
    }

    @PostMapping("/refresh")
    public TokenPair refresh(@Valid @RequestBody RefreshRequest request) {
        return auth.refresh(request.refreshToken());
    }

    public record RegisterRequest(
            @NotBlank @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$", message = "numéro au format international, par exemple +212600000000")
            String phone,
            @NotBlank @Size(min = 10, max = 128, message = "entre 10 et 128 caractères")
            String password,
            @NotBlank @Size(max = 40)
            String displayName) {
    }

    public record LoginRequest(@NotBlank String phone, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    /** Profil renvoyé au client : jamais le mot de passe ni son empreinte. */
    public record UserResponse(UUID id, String phone, boolean phoneVerified, String displayName, String platformRole) {
        static UserResponse from(UserEntity user) {
            return new UserResponse(user.getId(), user.getPhone(), user.isPhoneVerified(),
                    user.getDisplayName(), user.getPlatformRole().name());
        }
    }
}
