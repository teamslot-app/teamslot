package ma.teamslot.match.adapter.out.persistence;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import ma.teamslot.match.domain.Participant;
import ma.teamslot.match.domain.ParticipantStatus;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Forme des documents de la collection matches (SCRUM-112, ADR-017) : un document par match,
 * avec ses participants et leur côté (A ou B) dans le tableau participants.
 * Les UUID sont stockés en texte, comme dans l'outbox de libs/events (lisibles dans mongosh).
 */
final class MatchDocuments {

    static final String COLLECTION = "matches";

    static final String ID = "_id";
    static final String STATUT = "status";
    static final String ORGANISATEUR = "ownerId";
    static final String VERSION = "version";
    static final String PARTICIPANTS = "participants";

    // Champs d'un participant (élément du tableau participants)
    static final String P_ID = "id";
    static final String P_COMPTE = "userId";
    static final String P_SURNOM = "guestName";
    /** Surnom sans espaces et en minuscules : un surnom n'est utilisé qu'une fois par match (décision PO). */
    static final String P_CLE_SURNOM = "guestKey";
    static final String P_STATUT = "status";
    static final String P_AJOUTE_PAR = "addedBy";
    static final String P_CREE_LE = "createdAt";
    static final String P_COTE = "side";

    private MatchDocuments() {
    }

    static Query parId(UUID matchId) {
        return Query.query(Criteria.where(ID).is(matchId.toString()));
    }

    /** Même règle que l'ancien index PostgreSQL : lower(replace(guest_name, ' ', '')). */
    static String cleSurnom(String surnom) {
        return surnom == null ? null : surnom.replace(" ", "").toLowerCase(Locale.ROOT);
    }

    static Document participant(Participant p, UUID ajoutePar) {
        return new Document(P_ID, p.id().toString())
                .append(P_COMPTE, texte(p.userId()))
                .append(P_SURNOM, p.guestName())
                .append(P_CLE_SURNOM, cleSurnom(p.guestName()))
                .append(P_STATUT, p.status().name())
                .append(P_AJOUTE_PAR, ajoutePar.toString())
                .append(P_CREE_LE, new Date())
                .append(P_COTE, null);
    }

    static Participant participant(UUID matchId, Document d) {
        return new Participant(UUID.fromString(d.getString(P_ID)), matchId,
                uuid(d.getString(P_COMPTE)), d.getString(P_SURNOM),
                ParticipantStatus.valueOf(d.getString(P_STATUT)));
    }

    /** Participants dans l'ordre d'ajout (le tableau garde l'ordre d'insertion). */
    static List<Document> participants(Document match) {
        List<Document> liste = match.getList(PARTICIPANTS, Document.class);
        return liste == null ? List.of() : new ArrayList<>(liste);
    }

    static String texte(UUID id) {
        return id == null ? null : id.toString();
    }

    static UUID uuid(String texte) {
        return texte == null ? null : UUID.fromString(texte);
    }
}
