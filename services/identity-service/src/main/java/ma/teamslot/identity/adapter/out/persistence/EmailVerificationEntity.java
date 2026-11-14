package ma.teamslot.identity.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** SCRUM-133 — code de vérification de l'e-mail en cours (un seul par compte), stocké haché. */
@Entity
@Table(name = "email_verification")
public class EmailVerificationEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EmailVerificationEntity() {
        // pour JPA
    }

    public EmailVerificationEntity(UUID userId, String codeHash, Instant expiresAt, Instant createdAt) {
        this.userId = userId;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.attempts = 0;
    }

    /** Un nouveau code remplace l'ancien : nouvelle empreinte, nouvelle échéance, essais remis à zéro. */
    public void remplacer(String nouveauHash, Instant nouvelleEcheance, Instant maintenant) {
        this.codeHash = nouveauHash;
        this.expiresAt = nouvelleEcheance;
        this.createdAt = maintenant;
        this.attempts = 0;
    }

    public void compterUnEssai() {
        this.attempts++;
    }

    public UUID getUserId() { return userId; }
    public String getCodeHash() { return codeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getAttempts() { return attempts; }
}
