# Publier et consommer un événement (outbox) — SCRUM-70

Le code commun est dans le module partagé `libs/events` (ADR-015, SCRUM-88), paquet `ma.teamslot.events`.
Un service l'obtient en ajoutant la dépendance `ma.teamslot:events` : l'auto-configuration crée `Outbox`, `EventReader`, `IdempotentProcessor`, le relais et la gestion du `.dlt`. Les classes de `ma.teamslot.events.internal` ne sont pas à utiliser dans un service.
Règles du catalogue : voir `docs/events.md`.

## Publier un événement

```java
@Transactional
public Match creer(CreerMatch commande) {
    Match match = matchRepository.save(new Match(...));
    outbox.record(match.id(), "match.created", new MatchCreatedData(...));
    return match;
}
```

- Appeler `outbox.record` **dans la même transaction** que le changement d'état. Hors transaction, l'appel est refusé (`IllegalTransactionStateException`).
- `type` = nom du topic **exactement** comme dans `docs/events.md` (format `domaine.evenement`). Un nouvel événement s'ajoute au catalogue **dans la même PR** que le code qui le produit.
- `aggregateId` = la clé de la colonne « Clé » du catalogue (`matchId`, `venueId`…) : tous les événements d'un même agrégat arrivent dans l'ordre.
- `data` : le strict nécessaire. Jamais de mot de passe, de jeton, ni de téléphone ou d'e-mail d'invité.
- **Ne jamais appeler `KafkaTemplate` directement depuis le code métier.**

Le relais (`OutboxRelay`) publie toutes les secondes les lignes non publiées, dans l'ordre, puis remplit `published_at`. Livraison « au moins une fois » : un doublon est possible, le consommateur le neutralise.

**Un seul pod par service (`replicas: 1`).** Avec plusieurs pods, `SKIP LOCKED` répartit les lignes entre eux : deux événements du même agrégat (ex. `match.created` puis `match.confirmed`) peuvent alors partir dans le désordre. Avant de passer à plusieurs réplicas : un verrou par agrégat ou un seul relais actif (élection de leader).

## Consommer un événement

```java
@KafkaListener(topics = "match.created")
public void surMatchCree(String message) {
    EventEnvelope<JsonNode> evenement = lecteur.lire(message);          // EventReader
    processeur.traiterUneFois(evenement.eventId(), () ->               // IdempotentProcessor
            paiements.creer(evenement.data()));
}
```

- Le groupe de consommateurs est le nom du service (`spring.application.name`).
- `traiterUneFois` enregistre l'`eventId` dans `processed_event` **dans la même transaction** que le traitement : un doublon est ignoré, un traitement en échec pourra être retraité.
- Un champ inconnu dans `data` est ignoré (règle d'évolution du catalogue).

## Erreurs et `.dlt`

- Erreur passagère : 2 nouveaux essais, à 1 seconde d'intervalle.
- Message invalide (JSON cassé, enveloppe incomplète) : aucun nouvel essai.
- Ensuite, le message part dans `<topic>.dlt` (exemple : `match.created.dlt`) et les suivants sont traités.
- Le `.dlt` d'un topic doit exister : il est créé avec lui par `deploy/k8s/base/create-topics.sh`. Sa partition est choisie par Kafka, donc inutile d'avoir le même nombre de partitions que le topic.
- Le contenu d'un message n'est jamais écrit dans les logs : seuls l'`eventId`, le type et le nom de l'erreur le sont.

## Configuration

| Réglage | Valeur |
|---|---|
| Adresse du broker | variable `KAFKA_BOOTSTRAP_SERVERS` (défaut `localhost:9092`, voir `docs/kafka.md`) |
| Migration | `V2__outbox.sql` (tables `outbox_event`, `processed_event`) ; V1 = schéma initial du service, V2 = outbox, **V3** et suivants = évolutions |
| Relais planifié | `teamslot.outbox.relay.enabled` (désactivé dans les tests), `teamslot.outbox.relay.delay-ms` (1000 par défaut) |
| Délais du producteur | `max.block.ms=5000`, `request.timeout.ms=5000`, `delivery.timeout.ms=10000` : à changer ensemble (`delivery` ≥ `request` + `linger.ms`). Le relais attend 15 s, plus que `delivery`, pour logger la vraie cause (classe seulement, jamais le payload). |

**Passer un service à `libs/events`** (une PR par service, ordre de l'ADR-015) :
1. ajouter la dépendance `ma.teamslot:events` (`<version>${project.version}</version>`) et le module dans le `pom.xml` racine ;
2. **dans le même commit**, supprimer la copie du package `events/` **et** `V2__outbox.sql` du service (sinon Flyway trouve deux V2 et refuse de démarrer) ;
3. garder les lignes `spring.kafka.*` de `application.properties` et `teamslot.outbox.relay.enabled=false` dans `application-test.properties`.

`V2__outbox.sql` est déjà appliquée en staging : elle n'est **jamais** modifiée (test d'empreinte `MigrationOutboxTest` dans `libs/events`). Une évolution de l'outbox = une nouvelle migration.

## Tester

Les tests utilisent Testcontainers (PostgreSQL 16, Kafka `apache/kafka:4.1.0`) : **Docker Desktop doit être lancé**.
Le comportement commun est testé une seule fois, dans `libs/events` : `./mvnw -pl libs/events -am verify`.
Exemples pour les tests d'un service : `JdbcOutboxTests` (publication), `OutboxRelayTests` (relais), `KafkaConsommateurTests` (consommateur, doublon, `.dlt`).

## Prévu au Sprint 2

- Supprimer ou archiver les lignes publiées anciennes de `outbox_event`.
- Faire passer match-service, payment-service et venue-service à `libs/events` (ils ont encore leur copie du package `events`).
