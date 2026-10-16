package ma.teamslot.match.adapter.out.persistence;

import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.COLLECTION;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.ID;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.STATUT;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.VERSION;

import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;
import ma.teamslot.match.application.MatchRepository;
import ma.teamslot.match.domain.Match;
import org.bson.Document;
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
                .append("openSpots", m.openSpots())
                .append("ownerId", m.ownerId().toString())
                .append("payerId", m.payerId().toString())
                .append("venueId", m.venueId().toString())
                .append("slotId", m.slotId().toString())
                .append("startsAt", Date.from(m.startsAt()))
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
}
