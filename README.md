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

## Conventions techniques

Valables pour tous les services. En cas de doute, `services/service-template` fait foi.

### Ports locaux

| Composant | Port |
|---|---|
| gateway | 8080 |
| venue-service | 8081 |
| match-service | 8082 |
| identity-service | 8083 |
| team-service | 8084 |
| payment-service | 8085 |
| front (Vite / nginx dans compose) | 5173 / 3000 |

`service-template` écoute aussi sur 8081 mais ne tourne qu'en conteneur (modèle et démo). Dans Docker et Kubernetes, chaque conteneur a son propre réseau : les doublons ne comptent que pour les lancements depuis l'IDE.

### Noms

| Élément | Règle | Exemple |
|---|---|---|
| Dossier et artifactId | `<nom>-service` | `services/venue-service` |
| Paquet Java | `ma.teamslot.<nom>` | `ma.teamslot.venue` |
| `spring.application.name` | `<nom>-service` | `venue-service` |
| Image | `ghcr.io/teamslot-app/<dossier>` | `ghcr.io/teamslot-app/venue-service` |
| Base PostgreSQL | `<nom>`, une base par service | `venue` |

Paquets d'un service : `domain`, `application`, `adapter.in.web`, `adapter.out.persistence`, `events` (outbox, fourni par le modèle). Autres paquets selon le besoin (`config`, `security`).

### Migrations Flyway

- `V1` : schéma initial du service (remplace le baseline vide du modèle).
- `V2` : outbox, fournie par le modèle, à ne pas modifier.
- `V3` et suivants : évolutions.
- Une migration déjà fusionnée ne se modifie jamais.

### Profils et variables d'environnement

Profils : `local` (IDE), `demo` (docker-compose), `staging` (Kubernetes), `test` (tests).

| Variable | Rôle |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | connexion PostgreSQL |
| `KAFKA_BOOTSTRAP_SERVERS` | adresse du broker Kafka |
| `JWT_JWK_SET_URI` | clé publique des jetons, publiée par identity (défaut `http://localhost:8083/.well-known/jwks.json`) |
| `JWT_ISSUER` | émetteur attendu (`teamslot-identity`) |
| `IDENTITY_URL`, `VENUE_URL`, `MATCH_URL`, `PAYMENT_URL` | cibles des routes de la passerelle |

Jamais de secret dans le dépôt.

### Tests

Les tests d'intégration tournent sur un vrai PostgreSQL avec Testcontainers : Docker Desktop doit être lancé pour `./mvnw verify`. H2 n'est plus utilisé.

### Kafka

Topics : `domaine.événement` (ex. `slot.reserved`), clé = identifiant de l'agrégat, topic `.dlt` pour les erreurs. Liste complète dans `docs/events.md`. Montants : entiers en centimes (30000 = 300,00).

### Erreurs

Toutes les erreurs HTTP suivent Problem Details (RFC 9457), type `application/problem+json`, avec le champ `correlationId` en plus. Statuts utilisés : 400, 401, 403, 404, 409.

```json
{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "detail": "Seul l'organisateur peut lancer la répartition.",
  "instance": "/api/v1/matches/42/split",
  "correlationId": "7f3c1e0a-5b0e-4a77-9c42-1f6c2d9b8e10"
}
```

### Logs et identifiant de corrélation

Une ligne JSON par événement, sur la sortie standard :

```json
{"timestamp":"2026-10-05T09:12:41.532Z","level":"INFO","service":"match-service","correlationId":"7f3c1e0a-5b0e-4a77-9c42-1f6c2d9b8e10","userId":"42","message":"Match créé"}
```

`userId` n'apparaît que si l'utilisateur est connu. Ne jamais journaliser un jeton, un mot de passe ou un secret.

La passerelle crée `X-Correlation-Id` (UUID) s'il est absent, le transmet aux services et le renvoie dans la réponse. Chaque service le place dans le contexte de log et le copie dans les en-têtes des messages Kafka qu'il produit.

Le service modèle implémentera le format d'erreur et les logs JSON (SCRUM-25) ; les services créés à partir du modèle en hériteront.

## Documentation
- `docs/adr/` — décisions d'architecture
- `docs/api/` — contrats OpenAPI
- `docs/events.md` — catalogue des événements Kafka
- `docs/security/threat-model.md` — modèle de menaces
- `docs/cluster.md` — cluster local k3d
