# TeamSlot

Réserver un terrain de proximité pour son groupe d'amis, compléter son équipe
ou défier d'autres équipes, sans appeler le gérant.

## Le problème
Aujourd'hui, réserver un terrain de proximité passe par un appel au gérant,
sans visibilité sur les créneaux libres ni sur les prix. Compléter une équipe
ou trouver un adversaire se fait au bouche-à-oreille.

## Ce que fait TeamSlot
- Réservation de créneaux d'1 h, prix selon le terrain, chasubles en option
- Équipes créées par un joueur qui invite les autres ; un joueur peut être
  dans plusieurs équipes
- Recherche de joueurs pour compléter une équipe
- Défis entre équipes
- Paiement par le responsable : en ligne ou sur place
- Vote du joueur du match (MVP) après chaque match

## Ce que l'équipe veut apprendre
- Microservices Spring Boot et Kafka
- CI/CD avec GitHub Actions, GitOps avec Argo CD, Kubernetes (k3d)
- Sécurité automatisée : Gitleaks, SonarCloud, Trivy, OWASP ZAP

## Stack
Java 21 · Spring Boot · Kafka · React + TypeScript · Docker · k3d · Kustomize ·
Argo CD · Grafana · GitHub Actions / GHCR · Jira (Scrum)

## Équipe (Sprint 0)
| Chantier | Qui |
|---|---|
| A. Dépôt et CI | Sara |
| B. Cluster et GitOps | Amira |
| C. Contrats et cadrage | Laila |
| D. Squelette applicatif | Aya |

## Documentation
- `docs/adr/` — décisions d'architecture
- `docs/api/` — contrats OpenAPI
- `docs/events.md` — catalogue des événements Kafka
- `docs/security/threat-model.md` — modèle de menaces
- `docs/cluster.md` — cluster local k3d