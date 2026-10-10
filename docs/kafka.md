# Kafka

## En local (docker-compose)

Un seul broker en mode KRaft (sans ZooKeeper), image `apache/kafka:4.1.0`, tas Java limité à 512 Mo (environ 310 Mo mesurés au repos), données dans le volume `kafka-data`.

```
docker compose -f deploy/docker-compose.yml up -d kafka kafka-init
```

| Qui se connecte | Adresse |
|---|---|
| Les conteneurs du compose | `kafka:19092` (variable `KAFKA_BOOTSTRAP_SERVERS`) |
| Une application lancée depuis l'IDE | `localhost:9092` (lié à `127.0.0.1` seulement) |

Le service `kafka-init` crée les topics du Sprint 1 (`deploy/k8s/base/create-topics.sh`) : 9 topics et leurs 9 `.dlt`, 1 partition, réplication 1. Chaque `.dlt` a le même nombre de partitions que son topic (Spring Kafka renvoie le message dans la même partition). La création automatique de topics est désactivée : une faute de frappe ne crée pas de topic fantôme. Pour ajouter un topic, modifier le script.

## Tester un message

Sous Git Bash (Windows), lance d'abord `export MSYS_NO_PATHCONV=1`.

```
echo '{"test":"bonjour"}' | docker compose -f deploy/docker-compose.yml exec -T kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:19092 --topic user.registered
docker compose -f deploy/docker-compose.yml exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:19092 --topic user.registered --from-beginning --max-messages 1 --timeout-ms 20000
```

Pour vérifier la persistance, redémarrer le broker, attendre qu'il soit `healthy`, puis relire le message :

```
docker compose -f deploy/docker-compose.yml restart kafka
until [ "$(docker inspect -f '{{.State.Health.Status}}' deploy-kafka-1)" = "healthy" ]; do sleep 3; done
```

## Dans le cluster (staging)

À compléter avec le déploiement (SCRUM-64, avec Amira).

## Sécurité

Pas d'authentification ni de TLS : le broker n'est joignable que depuis la machine locale (compose) ou depuis le cluster. Les ACL sont au backlog, risque accepté dans le modèle de menaces.

## Pièges

- Sous Git Bash (Windows), sans `MSYS_NO_PATHCONV=1`, `/opt/kafka/...` est transformé en chemin Windows et la commande échoue.
- Après `docker compose restart kafka`, attendre que le broker soit `healthy` avant de lire (commande ci-dessus), sinon le consommateur expire.
- Ne jamais créer deux topics qui ne diffèrent que par `.` et `_` (collision des métriques Kafka).

## Ajouter un type d'événement

Le relais de l'outbox envoie chaque événement dans le topic qui porte le nom de son type (par exemple `match.created`). Les topics ne sont pas créés automatiquement (`auto.create.topics.enable=false`) : un type sans topic **bloque le relais**, qui réessaie sans fin et retarde tous les événements suivants du service.

Pour ajouter un type d'événement :

1. Ajouter son topic, et son `.dlt`, dans la liste de `deploy/k8s/base/create-topics.sh`.
2. Mettre à jour le catalogue `docs/events.md`.
3. En local, recréer les topics : `docker compose -f deploy/docker-compose.yml up kafka-init`.
4. Dans le cluster, rejouer le même script (la procédure sera précisée quand Kafka y sera déployé).
