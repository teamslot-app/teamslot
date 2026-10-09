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
