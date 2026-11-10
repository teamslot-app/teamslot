package ma.teamslot.match.adapter.out.persistence;

import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.COLLECTION;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.DEBUT;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.ID;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.MAX_JOUEURS;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.ORGANISATEUR;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.PARTICIPANTS;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.PLACES_OUVERTES;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.P_COMPTE;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.P_STATUT;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.STATUT;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.VERSION;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ma.teamslot.match.application.MatchOuvert;
import ma.teamslot.match.application.MatchPourAnnulation;
import ma.teamslot.match.application.MatchPourPlaces;
import ma.teamslot.match.application.MatchRepository;
import ma.teamslot.match.domain.Match;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/** Port MatchRepository sur MongoDB (SCRUM-112) : remplace JdbcMatchRepository, même comportement. */
@Repository
public class MongoMatchRepository implements MatchRepository {

    private final MongoTemplate mongo;

    public MongoMatchRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void save(Match m) {
        Document match = new Document(ID, m.id().toString())
                .append("kind", m.kind())
                .append(STATUT, m.status())
                .append("visibility", m.visibility())
                .append(PLACES_OUVERTES, m.openSpots())
                .append(MAX_JOUEURS, m.maxPlayers())
                .append("ownerId", m.ownerId().toString())
                .append("payerId", m.payerId().toString())
                .append("venueId", m.venueId().toString())
                .append("slotId", m.slotId().toString())
                .append(DEBUT, Date.from(m.startsAt()))
                .append("totalAmountCents", m.totalAmountCents())
                .append("currency", m.currency())
                .append("acceptsOnSitePayment", m.acceptsOnSitePayment())
                .append(VERSION, m.version())
                .append("createdAt", new Date())
                .append(MatchDocuments.PARTICIPANTS, new ArrayList<Document>());
        mongo.insert(match, COLLECTION);
    }

    /** Une seule écriture conditionnelle : atomique, sans lecture préalable (comme l'UPDATE ... WHERE de PostgreSQL). */
    @Override
    public boolean confirmer(UUID matchId) {
        Query enAttente = Query.query(Criteria.where(ID).is(matchId.toString()).and(STATUT).is("PENDING_PAYMENT"));
        Update confirme = new Update().set(STATUT, "CONFIRMED").inc(VERSION, 1);
        return mongo.updateFirst(enAttente, confirme, COLLECTION).getModifiedCount() == 1;
    }

    @Override
    public Optional<MatchPourAnnulation> pourAnnulation(UUID matchId) {
        Query query = MatchDocuments.parId(matchId);
        query.fields().include(MatchDocuments.ORGANISATEUR, STATUT);
        return Optional.ofNullable(mongo.findOne(query, Document.class, COLLECTION))
                .map(m -> new MatchPourAnnulation(UUID.fromString(m.getString(MatchDocuments.ORGANISATEUR)),
                        m.getString(STATUT)));
    }

    /** Écriture conditionnelle sur le statut : deux annulations simultanées, une seule réussit. */
    @Override
    public boolean annuler(UUID matchId) {
        Query annulable = Query.query(Criteria.where(ID).is(matchId.toString())
                .and(STATUT).in("PENDING_PAYMENT", "CONFIRMED"));
        Update annule = new Update().set(STATUT, "CANCELLED").inc(VERSION, 1);
        return mongo.updateFirst(annulable, annule, COLLECTION).getModifiedCount() == 1;
    }

    /** Lecture des seuls champs utiles : pas de surnom d'invité chargé pour compter les joueurs. */
    @Override
    public Optional<MatchPourPlaces> pourPlaces(UUID matchId) {
        Query query = MatchDocuments.parId(matchId);
        query.fields().include(ORGANISATEUR, STATUT, MAX_JOUEURS, VERSION,
                PARTICIPANTS + "." + P_COMPTE, PARTICIPANTS + "." + P_STATUT);
        return Optional.ofNullable(mongo.findOne(query, Document.class, COLLECTION)).map(m -> {
            List<MatchPourPlaces.Inscrit> inscrits = MatchDocuments.participants(m).stream()
                    .map(p -> new MatchPourPlaces.Inscrit(MatchDocuments.uuid(p.getString(P_COMPTE)),
                            p.getString(P_STATUT)))
                    .toList();
            return MatchPourPlaces.de(UUID.fromString(m.getString(ORGANISATEUR)), m.getString(STATUT),
                    (int) MatchDocuments.nombre(m, MAX_JOUEURS, Match.MAX_JOUEURS_PAR_DEFAUT),
                    MatchDocuments.nombre(m, VERSION, 0), inscrits);
        });
    }

    /**
     * Une seule écriture conditionnelle (findAndModify) : organisateur, statut ouvert et version inchangée.
     * Un participant ajouté entre-temps change la version : rien n'est écrit, le service répond 409.
     */
    @Override
    public Optional<Match> ouvrirPlaces(UUID matchId, UUID organisateur, long version, int openSpots) {
        Query condition = Query.query(Criteria.where(ID).is(matchId.toString())
                .and(ORGANISATEUR).is(organisateur.toString())
                .and(STATUT).in("PENDING_PAYMENT", "CONFIRMED")
                .and(VERSION).is(version));
        Update ouvre = new Update().set(PLACES_OUVERTES, openSpots).inc(VERSION, 1);
        Document apres = mongo.findAndModify(condition, ouvre, FindAndModifyOptions.options().returnNew(true),
                Document.class, COLLECTION);
        return Optional.ofNullable(apres).map(MongoMatchRepository::versMatch);
    }

    /**
     * Index partiel idx_matches_places_ouvertes (CollectionMatches) : seuls les matchs avec des places y sont.
     * Projection limitée aux champs publics : aucun participant ne sort de la base pour cette liste.
     */
    @Override
    public List<MatchOuvert> ouverts(Instant depuis, Instant jusqua, int limite) {
        Query query = Query.query(Criteria.where(PLACES_OUVERTES).gt(0)
                        .and(DEBUT).gte(Date.from(depuis)).lt(Date.from(jusqua))
                        .and(STATUT).in("PENDING_PAYMENT", "CONFIRMED"))
                .with(Sort.by(Sort.Direction.ASC, DEBUT))
                .limit(limite);
        query.fields().include(ID, "venueId", DEBUT, PLACES_OUVERTES, MAX_JOUEURS);
        return mongo.find(query, Document.class, COLLECTION).stream()
                .map(d -> new MatchOuvert(UUID.fromString(d.getString(ID)), UUID.fromString(d.getString("venueId")),
                        d.getDate(DEBUT).toInstant(), (int) MatchDocuments.nombre(d, PLACES_OUVERTES, 0),
                        (int) MatchDocuments.nombre(d, MAX_JOUEURS, Match.MAX_JOUEURS_PAR_DEFAUT)))
                .toList();
    }

    static Match versMatch(Document d) {
        return new Match(UUID.fromString(d.getString(ID)), d.getString("kind"), d.getString(STATUT),
                d.getString("visibility"), (int) MatchDocuments.nombre(d, PLACES_OUVERTES, 0),
                (int) MatchDocuments.nombre(d, MAX_JOUEURS, Match.MAX_JOUEURS_PAR_DEFAUT),
                UUID.fromString(d.getString(ORGANISATEUR)), UUID.fromString(d.getString("payerId")),
                UUID.fromString(d.getString("venueId")), UUID.fromString(d.getString("slotId")),
                d.getDate(DEBUT).toInstant(), MatchDocuments.nombre(d, "totalAmountCents", 0),
                d.getString("currency"), Boolean.TRUE.equals(d.getBoolean("acceptsOnSitePayment")),
                MatchDocuments.nombre(d, VERSION, 0));
    }
}
