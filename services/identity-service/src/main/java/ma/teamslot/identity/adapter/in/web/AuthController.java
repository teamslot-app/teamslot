package ma.teamslot.identity.adapter.in.web;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ma.teamslot.identity.adapter.out.persistence.UserEntity;
import ma.teamslot.identity.application.AuthErrors;
import ma.teamslot.identity.application.AuthService;
import ma.teamslot.identity.application.VerificationEmailService;
import ma.teamslot.identity.application.TokenPair;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** SCRUM-57, SCRUM-132, SCRUM-133 — contrat docs/api/identity.yaml (2.1.0) : inscription, vérification de l'e-mail, connexion. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final VerificationEmailService verificationEmail;

    public AuthController(AuthService auth, VerificationEmailService verificationEmail) {
        this.auth = auth;
        this.verificationEmail = verificationEmail;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(auth.register(new AuthService.Inscription(request.phone(), request.password(),
                request.displayName(), request.email(), request.birthDate(), request.phoneCountry())));
    }

    /** SCRUM-133 : 204 si le code est bon ; sinon invalid-code (400), code-expired (410), too-many-attempts (429). */
    @PostMapping("/email/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        switch (verificationEmail.verifier(request.email(), request.code())) {
            case VERIFIE -> { }
            case CODE_INVALIDE -> throw new AuthErrors.InvalidCode();
            case CODE_EXPIRE -> throw new AuthErrors.CodeExpired();
            case TROP_D_ESSAIS -> throw new AuthErrors.TooManyAttempts();
        }
    }

    /** SCRUM-133 : toujours 202, que l'e-mail existe ou non (la réponse ne révèle rien). */
    @PostMapping("/email/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendEmailCode(@Valid @RequestBody ResendEmailCodeRequest request) {
        verificationEmail.renvoyer(request.email());
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
            // longueur, familles de caractères, etc. : PolitiqueMotDePasse, dans le service (SCRUM-132)
            @NotBlank @Size(max = 128)
            String password,
            @NotBlank @Size(max = 40)
            String displayName,
            // SCRUM-132 : facultatifs pendant la migration (contrat identity 2.0.0)
            @Email @Size(max = 254)
            String email,
            LocalDate birthDate,
            @Pattern(regexp = "^[A-Z]{2}$", message = "code pays ISO 3166-1, par exemple MA")
            String phoneCountry) {
        // Volontairement aucun champ de rôle : un platformRole ou accountType envoyé est ignoré (SCRUM-136)
    }

    public record VerifyEmailRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "code à 6 chiffres") String code) {
    }

    public record ResendEmailCodeRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    public record LoginRequest(@NotBlank String phone, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    /** Profil renvoyé au client : jamais le mot de passe ni son empreinte. */
    public record UserResponse(UUID id, String phone, String phoneCountry, boolean phoneVerified, String email,
                               boolean emailVerified, LocalDate birthDate, String displayName, String platformRole,
                               String managerStatus) {
        static UserResponse from(UserEntity user) {
            return new UserResponse(user.getId(), user.getPhone(), user.getPhoneCountry(), user.isPhoneVerified(),
                    user.getEmail(), user.isEmailVerified(), user.getBirthDate(), user.getDisplayName(),
                    user.getPlatformRole().name(), user.getManagerStatus().name());
        }
    }
}
