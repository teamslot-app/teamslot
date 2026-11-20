package ma.teamslot.identity.application;

import java.util.UUID;

import ma.teamslot.identity.adapter.out.persistence.UserEntity;
import ma.teamslot.identity.adapter.out.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SCRUM-95 : voir et modifier MON profil. L'identifiant vient toujours de la claim sub du jeton (UserController),
 * jamais de la requête : on ne peut ni lire ni modifier le profil d'un autre compte.
 */
@Service
public class ProfilService {

    static final int SURNOM_MAX = 40;

    private final UserRepository users;

    public ProfilService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserEntity monProfil(UUID moi) {
        return users.findById(moi).orElseThrow(AuthErrors.AccountNotFound::new);
    }

    /** Espaces retirés au début et à la fin ; 1 à 40 caractères ; pas de caractère de contrôle. */
    @Transactional
    public UserEntity changerSurnom(UUID moi, String surnom) {
        String propre = surnom == null ? "" : surnom.strip();
        if (propre.isEmpty() || propre.length() > SURNOM_MAX) {
            throw new AuthErrors.InvalidProfile("Le surnom doit faire de 1 à " + SURNOM_MAX + " caractères.");
        }
        if (propre.chars().anyMatch(Character::isISOControl)) {
            throw new AuthErrors.InvalidProfile("Le surnom contient un caractère non autorisé.");
        }
        UserEntity user = monProfil(moi);
        user.changerSurnom(propre); // enregistré à la fin de la transaction (entité gérée par JPA)
        return user;
    }
}
