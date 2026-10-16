package ma.teamslot.match.adapter.out.persistence;

import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.COLLECTION;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.ID;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.ORGANISATEUR;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.PARTICIPANTS;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.P_CLE_SURNOM;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.P_COMPTE;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.STATUT;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.VERSION;

import java.util.Optional;
import java.util.UUID;
import ma.teamslot.match.application.MatchPourAjout;
import ma.teamslot.match.application.ParticipantAlreadyAddedException;
import ma.teamslot.match.application.ParticipantAlreadyAddedException.Raison;
import ma.teamslot.match.application.ParticipantRepository;
import ma.teamslot.match.domain.Participant;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/** Port ParticipantRepository sur MongoDB (SCRUM-112) : remplace JdbcParticipantRepository, même comportement. */
@Repository
public class MongoParticipantRepository implements ParticipantRepository {

    private final MongoTemplate mongo;

    public MongoParticipantRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /**
     * Pas de FOR SHARE dans MongoDB : la transaction de AddParticipantService lit un instantané du match.
     * Si une autre transaction modifie le match (annulation) avant la fin de l'ajout, l'écriture de save
     * est en conflit et la transaction est annulée : jamais d'ajout à un match devenu fermé.
     */
    @Override
    public Optional<MatchPourAjout> matchPourAjout(UUID matchId) {
        Query query = MatchDocuments.parId(matchId);
        query.fields().include(ORGANISATEUR, STATUT);
        Document match = mongo.findOne(query, Document.class, COLLECTION);
        return Optional.ofNullable(match)
                .map(m -> new MatchPourAjout(UUID.fromString(m.getString(ORGANISATEUR)), m.getString(STATUT)));
    }

    /**
     * Ajout atomique, seulement si le compte (ou le surnom, sans espaces ni casse) n'est pas déjà dans le tableau.
     * Remplace les index uniques uq_participants_match_compte et uq_participants_match_surnom : un index unique
     * MongoDB ne compare pas les éléments d'un même tableau, c'est donc la condition de l'écriture qui le fait.
     */
    @Override
    public void save(Participant p, UUID ajoutePar) {
        Raison raison = p.userId() != null ? Raison.COMPTE : Raison.SURNOM;
        Criteria libre = p.userId() != null
                ? Criteria.where(PARTICIPANTS + "." + P_COMPTE).ne(p.userId().toString())
                : Criteria.where(PARTICIPANTS + "." + P_CLE_SURNOM).ne(MatchDocuments.cleSurnom(p.guestName()));
        Query query = Query.query(Criteria.where(ID).is(p.matchId().toString()).andOperator(libre));
        Update ajout = new Update().push(PARTICIPANTS, MatchDocuments.participant(p, ajoutePar)).inc(VERSION, 1);

        if (mongo.updateFirst(query, ajout, COLLECTION).getMatchedCount() == 0) {
            throw new ParticipantAlreadyAddedException(raison, null);
        }
    }
}
