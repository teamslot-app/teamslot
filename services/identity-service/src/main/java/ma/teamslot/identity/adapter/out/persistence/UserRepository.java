package ma.teamslot.identity.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    boolean existsByPhone(String phone);

    Optional<UserEntity> findByPhone(String phone);
}
