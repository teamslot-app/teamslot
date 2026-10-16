# ADR-017 — Persistance polyglotte : une base adaptée aux données de chaque service

Statut : proposé (Sprint 2, SCRUM-107)
Remplace : — (précise ADR-014, qui reste valable pour venue et payment)

## Contexte
À la revue du Sprint 1, l'encadrant a demandé d'utiliser des types de bases différents selon les services, pour pratiquer plusieurs technologies. Jusqu'ici, ADR-014 place les quatre services dans une seule instance PostgreSQL. ADR-001 impose déjà une base par service : changer de moteur pour un service ne touche donc pas les autres. Le cluster de staging tourne sur un PC limité à 6 Go de mémoire pour Docker (ADR-003).

## Décision
On choisit la base d'après la forme des données de chaque service :

| Service | Base | Raison |
|---|---|---|
| venue | PostgreSQL (inchangé) | anti-chevauchement des créneaux (`EXCLUDE USING gist`, `tstzrange`) : propre à PostgreSQL |
| identity | MySQL + Redis | comptes = lignes relationnelles simples ; refresh tokens à durée de vie = clés Redis avec TTL |
| match | MongoDB | un match, ses participants et leurs côtés = un document |
| payment | PostgreSQL (inchangé) | argent : transactions ACID et contraintes strictes |
| search | DynamoDB (inchangé, ADR-012) | recherche par proximité avec geohash ; DynamoDB Local en développement, AWS Always Free en fin de projet |

Ce qui ne change pas : les contrats REST (`docs/api`) et les événements Kafka (`docs/events.md`). Seuls les adaptateurs `adapter/out/persistence` changent ; les ports hexagonaux et les services d'application restent identiques.

### Ordre des tickets
1. SCRUM-107 : cet ADR, relu par l'équipe **avant** le début des tickets de migration.
2. Chaîne identity : SCRUM-108 (MySQL et Redis dans docker-compose et le cluster) → SCRUM-109 (identity passe à MySQL) → SCRUM-110 (refresh tokens dans Redis).
3. Chaîne match : SCRUM-115 (MongoDB en replica set dans docker-compose et le cluster) → SCRUM-111 (outbox et consommateur idempotent MongoDB dans `libs/events`) → SCRUM-112 (match passe à MongoDB).

Les deux chaînes sont indépendantes et avancent en parallèle. Dans chaque chaîne, l'infrastructure passe en premier.

### Retour arrière
Chaque migration est une PR. Si le staging casse : `git revert` du commit fusionné, puis Argo CD revient à la version précédente. Pour que ce retour reste possible, l'ancienne base PostgreSQL d'un service n'est supprimée qu'en dernier, après vérification en staging (test E2E vert). Les données de staging sont des données de test : on n'en migre aucune.

## Conséquences
+ Chaque service utilise un moteur adapté à ses données ; avec DynamoDB (ADR-012), l'équipe pratique cinq technologies de stockage (PostgreSQL, MySQL, Redis, MongoDB, DynamoDB).
+ La révocation d'un refresh token devient « supprimer la clé », et l'expiration est gérée par Redis (TTL).
+ Les contrats et les événements ne changent pas : le front et les autres services ne voient aucune différence.
- Trois bases de plus à sécuriser. MongoDB et Redis n'ont pas d'authentification par défaut : elle doit être activée. Chaque base a son utilisateur dédié, des mots de passe générés par la machine en secrets scellés, un Service ClusterIP seulement (aucun NodePort ni LoadBalancer), et en local des ports liés à 127.0.0.1.
- Plus de mémoire sur un PC limité à 6 Go : chaque nouvelle base a des `resources.limits`.
- L'outbox doit exister aussi pour MongoDB dans `libs/events` (SCRUM-111). Les transactions MongoDB, nécessaires pour écrire l'état et l'événement ensemble, demandent un replica set (SCRUM-115).
- Plus d'images à épingler et à analyser (Trivy, Dependabot).
- ADR-014 ne couvre plus que venue et payment (et la base `template`).

## Alternatives écartées
- **Paiement en NoSQL** : perte des transactions ACID et des contraintes strictes sur l'argent.
- **SQL Server ou Oracle pour avoir « quatre moteurs différents »** : trop lourds pour un PC limité à 6 Go, et le choix ne viendrait pas de la forme des données.
- **Garder PostgreSQL partout** : ne répond pas à la demande de l'encadrant.
