# Bases de données

Une instance PostgreSQL, cinq bases (template, identity, venue, match, payment), un utilisateur par base sans droit sur les autres (voir ADR-014).

## En local (docker-compose)
1. Dans `deploy/`, copier `.env.example` en `.env` et remplir les mots de passe (valeurs aléatoires, jamais commitées).
2. `docker compose up -d postgres`, attendre `healthy` dans `docker compose ps`.
3. Se connecter : `docker compose exec postgres psql -U pgadmin -d postgres` (administrateur) ou, depuis ta machine, `psql -h 127.0.0.1 -U venue_user -d venue` (mot de passe dans `.env`).
4. Le script d'initialisation ne s'exécute que sur un volume vide : pour le rejouer, `docker compose down -v`.

## Dans le cluster (staging)
Les mots de passe sont des SealedSecrets (`deploy/k8s/overlays/staging/sealed-secrets/`), un par service (`<service>-db`, clés `DB_USER` et `DB_PASSWORD`). Ne jamais commiter un Secret en clair.
Se connecter en administrateur : `kubectl exec -it -n staging deploy/postgres -- psql -U pgadmin -d postgres`.

## Ajouter une migration (Flyway)
- Les fichiers vont dans `src/main/resources/db/migration` du service.
- Nom : `V<numéro>__description.sql`. Ne jamais modifier une migration déjà appliquée : créer la suivante.
- Chaque service ne migre que sa propre base.

## Ajouter ou changer un secret
Créer le Secret en clair hors du dépôt, le sceller avec `kubeseal --cert pub-cert.pem --format yaml`, commiter seulement le fichier scellé. Si le cluster est recréé, il faut resceller (la clé de scellement est sauvegardée hors du dépôt).

## match-service : MongoDB (ADR-017, SCRUM-112)
Depuis SCRUM-112, match-service n'utilise plus PostgreSQL : ses données sont dans la base `match` du MongoDB (replica set `rs0`, SCRUM-115).

- **Collection `matches`** : un document par match, avec ses participants et leur côté (A ou B) dans le tableau `participants`. Les UUID sont stockés en texte.
- **Validateur JSON Schema** posé à la création de la collection : statuts, montant ≥ 0, surnom de 1 à 40 caractères, soit un compte soit un surnom (mêmes règles que les anciens `CHECK`). Le modifier ensuite demande `collMod` (rôle dbAdmin), donc `mongosh` en root : `match_user` n'a que `readWrite`.
- **Unicité dans un match** (un compte, ou un surnom sans espaces ni casse, une seule fois) : assurée par la condition de l'écriture (`$push` seulement si absent), car un index unique MongoDB ne compare pas les éléments d'un même tableau.
- **Index** créés au démarrage par `CollectionMatches` (`ownerId`, `participants.userId`) : pas de Flyway pour MongoDB.
- **Outbox et idempotence** : collections `outbox_event` et `processed_event`, dans la même transaction MongoDB (libs/events, SCRUM-111).
- **Local** : `docker compose up -d mongodb` (dans `deploy/`), puis `docker compose exec mongodb mongosh match`.
- **Staging** : `kubectl exec -it -n staging mongodb-0 -- mongosh -u root -p --authenticationDatabase admin match` (mot de passe demandé, jamais dans la commande).
- La base PostgreSQL `match` et le secret `match-db` restent jusqu'au test E2E vert en staging, puis sont retirés (plan de retour arrière d'ADR-017).
