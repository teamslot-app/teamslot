package ma.teamslot.identity.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    Optional<UserEntity> findByPhone(String phone);

    Optional<UserEntity> findByEmail(String email);

    /**
     * SCRUM-82 : surnoms qui COMMENCENT par le texte, sauf le mien, 20 au plus, triés.
     * StartingWith échappe % et _ (Spring Data) : ce sont de vrais caractères, jamais des jokers.
     * Casse ignorée grâce à la collation de la colonne (pas de UPPER() qui empêcherait l'index) ; vérifié par les tests.
     */
    List<UserEntity> findTop20ByDisplayNameStartingWithAndIdNotOrderByDisplayNameAsc(String debut, UUID moi);
}
