package ma.teamslot.identity.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationRepository extends JpaRepository<EmailVerificationEntity, UUID> {

    /** Verrou de ligne : deux vérifications simultanées ne peuvent pas compter le même essai deux fois. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from EmailVerificationEntity v where v.userId = :userId")
    Optional<EmailVerificationEntity> findForUpdate(@Param("userId") UUID userId);
}
