package ma.teamslot.identity.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import ma.teamslot.identity.domain.PlatformRole;

@Entity
@Table(name = "app_user")
public class UserEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 40)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_role", nullable = false, length = 20)
    private PlatformRole platformRole;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** SCRUM-132 : en minuscules, unique ; null pour les comptes créés avant l'inscription v2. */
    @Column(length = 254, unique = true)
    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "phone_country", length = 2)
    private String phoneCountry;

    protected UserEntity() {
        // pour JPA
    }

    public UserEntity(UUID id, String phone, String passwordHash, String displayName,
                      PlatformRole platformRole, Instant createdAt) {
        this.id = id;
        this.phone = phone;
        this.phoneVerified = false;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.platformRole = platformRole;
        this.createdAt = createdAt;
    }

    /** SCRUM-132 : inscription v2. L'e-mail n'est pas vérifié à la création (SCRUM-133). */
    public UserEntity(UUID id, String phone, String passwordHash, String displayName, PlatformRole platformRole,
                      Instant createdAt, String email, LocalDate birthDate, String phoneCountry) {
        this(id, phone, passwordHash, displayName, platformRole, createdAt);
        this.email = email;
        this.emailVerified = false;
        this.birthDate = birthDate;
        this.phoneCountry = phoneCountry;
    }

    public UUID getId() { return id; }
    public String getPhone() { return phone; }
    public boolean isPhoneVerified() { return phoneVerified; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public PlatformRole getPlatformRole() { return platformRole; }
    public Instant getCreatedAt() { return createdAt; }
    public String getEmail() { return email; }
    public boolean isEmailVerified() { return emailVerified; }
    public LocalDate getBirthDate() { return birthDate; }
    public String getPhoneCountry() { return phoneCountry; }
}
