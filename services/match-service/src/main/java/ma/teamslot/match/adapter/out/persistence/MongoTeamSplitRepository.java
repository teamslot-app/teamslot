package ma.teamslot.match.adapter.out.persistence;

import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.COLLECTION;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.ID;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.ORGANISATEUR;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.PARTICIPANTS;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.P_COMPTE;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.P_COTE;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.P_ID;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.P_STATUT;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.STATUT;
import static ma.teamslot.match.adapter.out.persistence.MatchDocuments.VERSION;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import ma.teamslot.match.application.MatchPourRepartition;
import ma.teamslot.match.application.ParticipantAvecCote;
import ma.teamslot.match.application.TeamSplitRepository;
import ma.teamslot.match.domain.Participant;
import ma.teamslot.match.domain.ParticipantStatus;
import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/** Port TeamSplitRepository sur MongoDB (SCRUM-112) : remplace JdbcTeamSplitRepository, même comportement. */
@Repository
public class MongoTeamSplitRepository implements TeamSplitRepository {

    /** Participants tirés au sort : ni REQUESTED, ni DECLINED. */
    private static final Set<String> JOUEURS = Set.of(ParticipantStatus.INVITED.name(), ParticipantStatus.CONFIRMED.name());

    private final MongoTemplate mongo;

    public MongoTeamSplitRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /**
     * Équivalent du SELECT ... FOR UPDATE : la transaction écrit tout de suite dans le match (version + 1).
     * Un second tirage simultané du même match entre alors en conflit d'écriture et sa transaction est annulée :
     * deux tirages ne se mélangent jamais. Rien n'est écrit si le match n'existe pas.
     */
    @Override
    public Optional<MatchPourRepartition> verrouillerMatch(UUID matchId) {
        Query query = MatchDocuments.parId(matchId);
        query.fields().include(ORGANISATEUR, STATUT);
        Document match = mongo.findAndModify(query, new Update().inc(VERSION, 1),
                FindAndModifyOptions.options().returnNew(true), Document.class, COLLECTION);
        return Optional.ofNullable(match)
                .map(m -> new MatchPourRepartition(UUID.fromString(m.getString(ORGANISATEUR)), m.getString(STATUT)));
    }

    /** Sans effet si l'organisateur est déjà dans le tableau, quel que soit son statut (ancien ON CONFLICT DO NOTHING). */
    @Override
    public void inscrireOrganisateur(UUID matchId, UUID organisateur) {
        Query absent = Query.query(Criteria.where(ID).is(matchId.toString())
                .and(PARTICIPANTS + "." + P_COMPTE).ne(organisateur.toString()));
        Participant inscrit = new Participant(UUID.randomUUID(), matchId, organisateur, null, ParticipantStatus.CONFIRMED);
        mongo.updateFirst(absent, new Update().push(PARTICIPANTS, MatchDocuments.participant(inscrit, organisateur)),
                COLLECTION);
    }

    @Override
    public List<Participant> joueurs(UUID matchId) {
        return documentsParticipants(matchId).stream()
                .filter(p -> JOUEURS.contains(p.getString(P_STATUT)))
                .map(p -> MatchDocuments.participant(matchId, p))
                .toList();
    }

    /** Tous les côtés remis à null, puis A et B posés, dans la transaction du tirage. */
    @Override
    public void enregistrerCotes(UUID matchId, List<UUID> coteA, List<UUID> coteB) {
        Query match = MatchDocuments.parId(matchId);
        mongo.updateFirst(match, new Update().set(PARTICIPANTS + ".$[]." + P_COTE, null), COLLECTION);
        poser(match, "A", coteA);
        poser(match, "B", coteB);
    }

    private void poser(Query match, String cote, List<UUID> ids) {
        if (ids.isEmpty()) {
            return;
        }
        List<String> textes = ids.stream().map(UUID::toString).toList();
        Update update = new Update().set(PARTICIPANTS + ".$[p]." + P_COTE, cote)
                .filterArray("p." + P_ID, new Document("$in", textes));
        mongo.updateFirst(match, update, COLLECTION);
    }

    @Override
    public Optional<UUID> organisateur(UUID matchId) {
        Query query = MatchDocuments.parId(matchId);
        query.fields().include(ORGANISATEUR);
        return Optional.ofNullable(mongo.findOne(query, Document.class, COLLECTION))
                .map(m -> UUID.fromString(m.getString(ORGANISATEUR)));
    }

    @Override
    public boolean aUnCompteParticipant(UUID matchId, UUID userId) {
        Criteria participant = Criteria.where(P_COMPTE).is(userId.toString()).and(P_STATUT).in(JOUEURS);
        return mongo.exists(Query.query(Criteria.where(ID).is(matchId.toString())
                .and(PARTICIPANTS).elemMatch(participant)), COLLECTION);
    }

    @Override
    public List<ParticipantAvecCote> participants(UUID matchId) {
        return documentsParticipants(matchId).stream()
                .map(p -> new ParticipantAvecCote(MatchDocuments.participant(matchId, p), p.getString(P_COTE)))
                .toList();
    }

    private List<Document> documentsParticipants(UUID matchId) {
        Query query = MatchDocuments.parId(matchId);
        query.fields().include(PARTICIPANTS);
        Document match = mongo.findOne(query, Document.class, COLLECTION);
        return match == null ? List.of() : MatchDocuments.participants(match);
    }
}
