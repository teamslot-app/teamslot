package ma.teamslot.match.adapter.out.persistence;

import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.COLLECTION;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.DEMANDES;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.D_COMPTE;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.D_LE;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.ID;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.ORGANISATEUR;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.PARTICIPANTS;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.PLACES_OUVERTES;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.P_COMPTE;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.STATUT;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.VERSION;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import ma.teamslot.match.application.DemandeRejoindre;
import ma.teamslot.match.application.JoinRequestRepository;
import ma.teamslot.match.application.MatchPourDemande;
import ma.teamslot.match.domain.Participant;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/** Port JoinRequestRepository sur MongoDB (SCRUM-79) : demandes dans le tableau joinRequests du match. */
@Repository
public class MongoJoinRequestRepository implements JoinRequestRepository {

    private static final List<String> STATUTS_OUVERTS = List.of("PENDING_PAYMENT", "CONFIRMED");

    private final MongoTemplate mongo;

    public MongoJoinRequestRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Lecture des seuls comptes : aucun surnom d'invité chargé. */
    @Override
    public Optional<MatchPourDemande> pourDemande(UUID matchId) {
        Query query = MatchDocuments.parId(matchId);
        query.fields().include(ORGANISATEUR, STATUT, PLACES_OUVERTES, PARTICIPANTS + "." + P_COMPTE,
                DEMANDES + "." + D_COMPTE);
        return Optional.ofNullable(mongo.findOne(query, Document.class, COLLECTION))
                .map(m -> new MatchPourDemande(UUID.fromString(m.getString(ORGANISATEUR)), m.getString(STATUT),
                        (int) MatchDocuments.nombre(m, PLACES_OUVERTES, 0),
                        comptes(MatchDocuments.participants(m), P_COMPTE), comptes(liste(m), D_COMPTE)));
    }

    @Override
    public boolean demander(UUID matchId, UUID joueur, Instant le) {
        String compte = joueur.toString();
        Query condition = Query.query(new Criteria().andOperator(
                Criteria.where(ID).is(matchId.toString()),
                Criteria.where(STATUT).in(STATUTS_OUVERTS),
                Criteria.where(PLACES_OUVERTES).gt(0),
                Criteria.where(ORGANISATEUR).ne(compte),
                Criteria.where(PARTICIPANTS + "." + P_COMPTE).ne(compte),
                Criteria.where(DEMANDES + "." + D_COMPTE).ne(compte)));
        Update ajout = new Update().push(DEMANDES, new Document(D_COMPTE, compte).append(D_LE, Date.from(le)));
        return mongo.updateFirst(condition, ajout, COLLECTION).getModifiedCount() == 1;
    }

    @Override
    public List<DemandeRejoindre> demandes(UUID matchId) {
        Query query = MatchDocuments.parId(matchId);
        query.fields().include(DEMANDES);
        Document match = mongo.findOne(query, Document.class, COLLECTION);
        if (match == null) {
            return List.of();
        }
        return liste(match).stream()
                .map(d -> new DemandeRejoindre(matchId, UUID.fromString(d.getString(D_COMPTE)),
                        d.getDate(D_LE).toInstant()))
                .toList();
    }

    @Override
    public boolean accepter(UUID matchId, UUID organisateur, Participant joueur) {
        String compte = joueur.userId().toString();
        Query condition = Query.query(new Criteria().andOperator(
                Criteria.where(ID).is(matchId.toString()),
                Criteria.where(ORGANISATEUR).is(organisateur.toString()),
                Criteria.where(STATUT).in(STATUTS_OUVERTS),
                Criteria.where(PLACES_OUVERTES).gt(0),
                Criteria.where(DEMANDES + "." + D_COMPTE).is(compte),
                Criteria.where(PARTICIPANTS + "." + P_COMPTE).ne(compte)));
        Update acceptation = new Update()
                .pull(DEMANDES, new Document(D_COMPTE, compte))
                .push(PARTICIPANTS, MatchDocuments.participant(joueur, organisateur))
                .inc(PLACES_OUVERTES, -1)
                .inc(VERSION, 1);
        return mongo.updateFirst(condition, acceptation, COLLECTION).getModifiedCount() == 1;
    }

    private static List<Document> liste(Document match) {
        List<Document> demandes = match.getList(DEMANDES, Document.class);
        return demandes == null ? List.of() : demandes;
    }

    private static Set<UUID> comptes(List<Document> elements, String champ) {
        return elements.stream()
                .map(e -> e.getString(champ))
                .filter(Objects::nonNull)
                .map(UUID::fromString)
                .collect(Collectors.toUnmodifiableSet());
    }
}
