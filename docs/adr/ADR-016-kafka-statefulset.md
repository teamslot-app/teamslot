# ADR-016 — Kafka en StatefulSet Kustomize, image officielle apache/kafka

Statut : accepté (Sprint 1, exception à ADR-008 validée par Laila)
Remplace : —

## Contexte
ADR-002 impose la communication par événements Kafka. En local, le broker tourne déjà avec docker-compose (SCRUM-64) : image `apache/kafka:4.1.0`, mode KRaft, un seul nœud, environ 310 Mo de mémoire mesurés avec un tas limité à 512 Mo. Il faut maintenant le déployer dans le cluster k3d, où Docker dispose de 6 Go (ADR-003). ADR-008 prévoit Helm pour les outils tiers, mais le projet Apache Kafka ne publie pas de chart officiel, et les images du chart Bitnami sont passées dans bitnamilegacy depuis août 2025, sans mises à jour de sécurité (voir ADR-014), ce qui contredit notre politique Trivy et Dependabot.

## Décision
Un broker Kafka en StatefulSet décrit en Kustomize (exception à ADR-008, comme ADR-014), avec l'image officielle `apache/kafka`, épinglée en version précise (`4.1.0`, la même qu'en local). Mode KRaft à un seul nœud (broker et contrôleur ensemble), sans ZooKeeper. Volume persistant (`volumeClaimTemplates`), Service interne seulement (aucune exposition hors du cluster), exécution en utilisateur non-root numérique, tas limité (`-Xms256m -Xmx512m`) et ressources bornées (requests 512 Mi, limite 1 Gi). Les topics ne sont pas créés automatiquement : un Job lance le même script `deploy/k8s/base/create-topics.sh` qu'en docker-compose. Facteur de réplication 1 partout.

## Conséquences
+ Même image et mêmes réglages en local et dans le cluster : ce qui marche sur un poste marche en staging.
+ Mémoire faible (environ 0,5 à 1 Go), compatible avec les 6 Go de Docker.
+ Image officielle, maintenue et analysée par Trivy, tag suivi par Dependabot.
+ Manifestes Kustomize lisibles, comme pour PostgreSQL.
- Un seul broker : une panne arrête les échanges, et la perte du volume efface les événements. Les lignes de l'outbox restent en base après publication (tant qu'on ne les purge pas), ce qui permettrait de les rejouer à la main.
- Le mode combiné broker et contrôleur n'est pas recommandé par le projet Kafka pour une production exigeante : acceptable pour notre usage, à revoir si la production change.
- Pas d'authentification ni de TLS : le broker n'est joignable que depuis le cluster.
- Mises à jour de version, sauvegardes et taille du volume à gérer nous-mêmes.

## Alternatives écartées
- Chart Helm Bitnami : images sans mises à jour de sécurité (voir contexte).
- Opérateur Strimzi : plus de composants à faire tourner (opérateur, CRD) et plus de mémoire, pour un seul broker. Pourra être étudié plus tard, comme CloudNativePG pour PostgreSQL.
- Trois brokers : ils multiplient la mémoire et la complexité (réplication, quorum) sans besoin de disponibilité en staging.
- ZooKeeper : supprimé depuis Kafka 4.0, remplacé par KRaft.
