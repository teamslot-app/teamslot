package ma.teamslot.identity.application;

import java.util.UUID;

/** SCRUM-82 — limite des recherches de joueurs par compte, pour qu'on ne puisse pas aspirer tous les surnoms. */
public interface LimiteRecherche {

    /** 0 si la recherche est permise (elle est alors comptée) ; sinon, les secondes à attendre. */
    long attendreAvantRecherche(UUID compte);
}
