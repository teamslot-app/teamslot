package ma.teamslot.venue.adapter.in.kafka;

import ma.teamslot.venue.application.ReleaseService;
import ma.teamslot.venue.events.EventEnvelope;
import ma.teamslot.venue.events.EventReader;
import ma.teamslot.venue.events.IdempotentProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * SCRUM-76 : lit match.cancelled et libère le créneau, une seule fois par événement (idempotent).
 * Pas d'appel synchrone de match vers venue (docs/events.md, section 5).
 * Désactivable avec teamslot.venue.consumers.enabled=false (tests sans Kafka).
 */
@Component
@ConditionalOnProperty(
    name = "teamslot.venue.consumers.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class MatchCancelledListener {

  static final String TOPIC = "match.cancelled";
  private static final int VERSION_LUE = 1;
  private static final Logger LOG = LoggerFactory.getLogger(MatchCancelledListener.class);

  private final EventReader lecteur;
  private final IdempotentProcessor processeur;
  private final JsonMapper json;
  private final ReleaseService service;

  public MatchCancelledListener(
      EventReader lecteur, IdempotentProcessor processeur, JsonMapper json, ReleaseService service) {
    this.lecteur = lecteur;
    this.processeur = processeur;
    this.json = json;
    this.service = service;
  }

  @KafkaListener(topics = TOPIC)
  public void recevoir(String message) {
    EventEnvelope<JsonNode> evenement = lecteur.lire(message);
    if (!TOPIC.equals(evenement.type()) || evenement.version() != VERSION_LUE) {
      LOG.info(
          "Evenement ignore {} ({} v{})",
          evenement.eventId(),
          evenement.type(),
          evenement.version());
      return;
    }
    MatchCancelledData data = json.treeToValue(evenement.data(), MatchCancelledData.class);
    processeur.traiterUneFois(evenement.eventId(), () -> service.surMatchAnnule(data.matchId()));
  }
}
