# Décisions d'architecture (ADR)

Un ADR (*Architecture Decision Record*) est une note courte qui garde la trace d'un choix : son contexte, la décision, ses conséquences et les alternatives écartées. On peut changer d'avis, mais on sait pourquoi on avait choisi.

| ADR | Décision | Statut |
|---|---|---|
| [001](ADR-001-microservices.md) | Microservices Spring Boot, une base par service | Accepté |
| [002](ADR-002-kafka-evenements.md) | Communication par événements Kafka (saga chorégraphiée) | Accepté |
| [003](ADR-003-cluster-k3d.md) | Cluster local k3d, accès par Tailscale | Accepté |
| [004](ADR-004-github-actions-ghcr-argocd.md) | GitHub Actions, GHCR et Argo CD (GitOps) | Accepté |
| [005](ADR-005-aws-always-free-fin-de-projet.md) | AWS Always Free, en fin de projet seulement | Accepté |
| [006](ADR-006-paiement-responsable.md) | Paiement par un responsable, en ligne ou sur place | Accepté |
| [007](ADR-007-equipes-joueurs.md) | Équipes créées et gérées par les joueurs, sans limite d'appartenance | Accepté |
| [008](ADR-008-kustomize-helm.md) | Kustomize pour nos manifestes, Helm pour les outils tiers | Accepté |
| [009](ADR-009-frontend-react.md) | Front-end React, TypeScript et Vite, mobile préparé | Accepté |
| [010](ADR-010-jira-et-github.md) | Jira pour le backlog, GitHub pour le code et la CI | Accepté |
| [011](ADR-011-sonarcloud.md) | SonarCloud pour l'analyse du code | Accepté |
| [012](ADR-012-recherche-geohash.md) | Recherche géographique par geohash dans DynamoDB | Accepté |
| [013](ADR-013-jwt-rs256-jwks.md) | Jetons JWT signés en RS256, clé publique publiée en JWKS | Accepté |
| [014](ADR-014-postgresql-une-instance.md) | Une instance PostgreSQL, quatre bases isolées par les droits | Proposé |
| [015](ADR-015-modules-partages.md) | Modules partagés et build Maven unique | Proposé |
| [016](ADR-016-kafka-statefulset.md) | Kafka KRaft en StatefulSet Kustomize, image officielle apache/kafka | Proposé |

## Faire évoluer une décision

1. On n'efface jamais un ADR : on écrit un **nouvel ADR** qui explique le problème, l'alternative et son coût.
2. Le nouvel ADR indique `Remplace : ADR-XXX` ; l'ancien passe au statut `remplacé par ADR-YYY`.
3. L'équipe décide en rétrospective, puis la pull request porte la clé du ticket Jira.

## Modèle

```markdown
# ADR-XXX — Titre

Statut : proposé | accepté (Sprint N) | remplacé par ADR-YYY  
Remplace : —

## Contexte
## Décision
## Conséquences
+ …
- …

## Alternatives écartées
```
