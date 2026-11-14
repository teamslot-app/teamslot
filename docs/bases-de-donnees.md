# Bases de données

Une instance PostgreSQL, quatre bases (template, venue, payment, notification), un utilisateur par base sans droit sur les autres (voir ADR-014 et ADR-017). identity-service utilise MySQL et Redis (voir plus bas), match-service utilise MongoDB.

## En local (docker-compose)
1. Dans `deploy/`, copier `.env.example` en `.env` et remplir les mots de passe (valeurs aléatoires, jamais commitées).
2. `docker compose up -d postgres`, attendre `healthy` dans `docker compose ps`.
3. Se connecter : `docker compose exec postgres psql -U pgadmin -d postgres` (administrateur) ou, depuis ta machine, `psql -h 127.0.0.1 -U venue_user -d venue` (mot de passe dans `.env`).
4. Le script d'initialisation ne s'exécute que sur un volume vide : pour le rejouer, `docker compose down -v`.

## Dans le cluster (staging)
Les mots de passe sont des SealedSecrets (`deploy/k8s/overlays/staging/sealed-secrets/`), un par service (`<service>-db`, clés `DB_USER` et `DB_PASSWORD`). Ne jamais commiter un Secret en clair.
Se connecter en administrateur : `kubectl exec -it -n staging deploy/postgres -- psql -U pgadmin -d postgres`.

## Ajouter une base PostgreSQL dans le staging existant (exemple : notification, SCRUM-128)
`init-databases.sh` ne s'exécute qu'à la création du volume de PostgreSQL : une base ajoutée ensuite se crée **une fois**, à la main, par une personne qui a le droit de lire les Secrets et d'exécuter dans le pod (Amira). Le mot de passe n'est **jamais affiché** : il est lu dans le Secret déchiffré par le cluster et passé à `psql` par l'entrée standard (ni à l'écran, ni dans l'historique, ni dans la liste des processus).

1. Fusionner la PR qui ajoute le SealedSecret `<service>-db` et attendre la synchronisation d'Argo CD (`kubectl get secret notification-db -n staging` doit exister).
2. Créer l'utilisateur et la base (le mot de passe ne contient que des lettres et des chiffres) :

   ```bash
   export KUBECONFIG=~/.kube/teamslot-staging.yaml
   ( MDP=$(kubectl get secret notification-db -n staging -o jsonpath='{.data.DB_PASSWORD}' | base64 -d)
     { printf '\\set pass %s\n' "$MDP"
       printf '%s\n' "CREATE USER notification_user WITH PASSWORD :'pass';" \
                     "CREATE DATABASE notification OWNER notification_user;" \
                     "REVOKE ALL ON DATABASE notification FROM PUBLIC;"
     } | kubectl exec -i -n staging deploy/postgres -- sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres' )
   ```

   Résultat attendu : `CREATE ROLE`, `CREATE DATABASE`, `REVOKE`.
3. Redémarrer le service pour qu'il se connecte tout de suite : `kubectl rollout restart deploy/notification-service -n staging`, puis vérifier `kubectl get pods -n staging -l app=notification-service` (READY 1/1) et les logs du Job `smoke-test` après la synchronisation suivante.
4. Sur un cluster neuf, ajouter aussi la base à `init-databases.sh` et la variable du mot de passe à `postgres.yaml` (non fait dans le staging existant : ce changement redémarrerait PostgreSQL pour rien).

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
- **Capacité d'un match (SCRUM-78)** : champ `maxPlayers` (organisateur compris), écrit à 10 à la création ; **absent** dans les matchs créés avant, et lu alors comme 10 (`Match.MAX_JOUEURS_PAR_DEFAUT`), sans migration. Le validateur du code le décrit (entier ≥ 2, facultatif). Une collection déjà créée (local, staging) accepte le champ telle quelle ; pour y ajouter la règle, une fois, en root :

  ```js
  const v = db.getCollectionInfos({ name: "matches" })[0].options.validator;
  v.$jsonSchema.properties.maxPlayers = { bsonType: ["int", "long"], minimum: 2 };
  db.runCommand({ collMod: "matches", validator: v });
  ```
- **Demandes pour rejoindre (SCRUM-79)** : tableau `joinRequests` (`userId`, `requestedAt`), absent tant qu'il n'y a pas de demande. Une demande ne change pas `version` (elle ne prend pas de place) ; l'acceptation retire la demande, ajoute le participant `CONFIRMED` et fait `openSpots - 1` en une seule écriture conditionnelle (`openSpots > 0`). Deux écritures simultanées dans des transactions : MongoDB refuse la seconde (`TransientTransactionError`), `TransactionsMongo` la rejoue (3 essais). Règle du validateur pour une collection déjà créée, en root :

  ```js
  const v = db.getCollectionInfos({ name: "matches" })[0].options.validator;
  v.$jsonSchema.properties.joinRequests = { bsonType: "array", items: { bsonType: "object",
    required: ["userId", "requestedAt"],
    properties: { userId: { bsonType: "string" }, requestedAt: { bsonType: "date" } } } };
  db.runCommand({ collMod: "matches", validator: v });
  ```
- **Unicité dans un match** (un compte, ou un surnom sans espaces ni casse, une seule fois) : assurée par la condition de l'écriture (`$push` seulement si absent), car un index unique MongoDB ne compare pas les éléments d'un même tableau.
- **Index** créés au démarrage par `CollectionMatches` (`ownerId`, `participants.userId`) : pas de Flyway pour MongoDB.
- **Outbox et idempotence** : collections `outbox_event` et `processed_event`, dans la même transaction MongoDB (libs/events, SCRUM-111).
- **Local** : `docker compose up -d mongodb` (dans `deploy/`), puis `docker compose exec mongodb mongosh match`.
- **Staging** : `kubectl exec -it -n staging mongodb-0 -- mongosh -u root -p --authenticationDatabase admin match` (mot de passe demandé, jamais dans la commande).
- La base PostgreSQL `match` et le secret `match-db` ont été retirés après le test E2E vert en staging du 16/10 (plan de retour arrière d'ADR-017).
- **Test E2E du parcours** : `deploy/e2e/parcours-match.sh`. Dans le cluster : `kubectl run e2e-match --rm -i --restart=Never -n staging --image=curlimages/curl:8.10.1 --command -- sh -s < deploy/e2e/parcours-match.sh`. Depuis un poste : `API=http://<passerelle> sh deploy/e2e/parcours-match.sh`.

## identity-service : MySQL et Redis (ADR-017, SCRUM-108, SCRUM-109)
Les comptes sont dans la base `identity` de MySQL 8.4 (utilisateur `identity_user`). Les refresh tokens sont dans Redis (SCRUM-110) : clés `refresh:<hash>` avec TTL, la table MySQL `refresh_token` n'existe plus (supprimée par V2).

- **Schéma** : migrations Flyway dans `services/identity-service/src/main/resources/db/migration` (syntaxe MySQL). Les UUID sont en `BINARY(16)`, les dates en `DATETIME(6)` et toujours en UTC.
- **Local** : `docker compose up -d mysql` (dans `deploy/`), puis `docker compose exec mysql mysql -uidentity_user -p identity` (mot de passe demandé, jamais dans la commande).
- **Staging** : les mots de passe sont dans les SealedSecrets `mysql-auth` (clés `root-password` et `identity-password`) et `redis-auth` (clé `password`). Connexion : `kubectl exec -it -n staging mysql-0 -- sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -uidentity_user identity'`.
- **Tests** : Testcontainers MySQL 8.4, plus de H2.
- **Reprise** : la base MySQL est neuve, les comptes de démo et de test sont à recréer après le déploiement. L'ancienne base PostgreSQL `identity` et son utilisateur seront supprimés plus tard.
