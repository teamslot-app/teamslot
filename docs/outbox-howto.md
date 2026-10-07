# Publier et consommer un événement (outbox) — SCRUM-70

Le code commun est dans le package `events` du service modèle (`services/service-template`).
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
- Le `.dlt` doit avoir **le même nombre de partitions** que son topic (topics créés par `deploy/kafka/create-topics.sh`).
- Le contenu d'un message n'est jamais écrit dans les logs : seuls l'`eventId`, le type et le nom de l'erreur le sont.

## Configuration

| Réglage | Valeur |
|---|---|
| Adresse du broker | variable `KAFKA_BOOTSTRAP_SERVERS` (défaut `localhost:9092`, voir `docs/kafka.md`) |
| Migration | `V2__outbox.sql` (tables `outbox_event`, `processed_event`) ; le schéma du service commence à **V3** |
| Relais planifié | `teamslot.outbox.relay.enabled` (désactivé dans les tests), `teamslot.outbox.relay.delay-ms` (1000 par défaut) |

**Service copié du modèle avant SCRUM-70 :** copier le package `events/` (adapter la ligne `package`), `V2__outbox.sql`, les lignes `spring.kafka.*` de `application.properties` et `teamslot.outbox.relay.enabled=false` dans `application-test.properties`.

## Tester

Les tests utilisent Testcontainers (PostgreSQL 16, Kafka `apache/kafka:4.1.0`) : **Docker Desktop doit être lancé**.
Exemples à copier : `JdbcOutboxTests` (publication), `OutboxRelayTests` (relais), `KafkaConsommateurTests` (consommateur, doublon, `.dlt`).

## Prévu au Sprint 2

- Supprimer ou archiver les lignes publiées anciennes de `outbox_event`.
- Déplacer le package `events` dans un module partagé (`libs/events`) pour éviter les copies.
