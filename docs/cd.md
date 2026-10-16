# Déploiement continu (CD)

Ce document décrit ce qui se passe après la fusion d'une PR sur `main`. Le schéma complet du pipeline, le statut GitHub et le retour arrière seront ajoutés par SCRUM-113.

## Test de fumée après chaque déploiement (SCRUM-99)

### Ce qu'il vérifie

Après chaque synchronisation du staging, Argo CD lance le Job `smoke-test` (hook `PostSync`, fichier `deploy/k8s/base/smoke-test.yaml`, script `deploy/k8s/base/smoke-test.sh`). Le Job :

1. affiche les **tags d'image déployés** (un par service, copiés depuis les Deployment par `deploy/k8s/overlays/staging/smoke-test-versions.yaml`) ;
2. vérifie que `/actuator/health` répond `UP` pour gateway, identity-service, venue-service, match-service, payment-service et service-template (jusqu'à environ 5 minutes par service) ;
3. vérifie que des routes protégées appelées **sans jeton** répondent `401` : la passerelle (`/api/v1/matches`) et, en direct, venue-service (SCRUM-87), match-service et payment-service.

Le Job n'utilise aucun jeton ni secret, ne monte aucun secret, et n'a pas accès à l'API Kubernetes (`automountServiceAccountToken: false`).

### Pourquoi dans le cluster

Le staging tourne sur le PC d'Amira (k3d, ADR-003) : GitHub Actions ne peut pas le joindre. Le hook tourne donc dans le cluster, à côté des services. Aucun port à ouvrir, aucun secret à donner à GitHub.

### Version déployée

Argo CD ne lance un hook `PostSync` qu'une fois **toutes** les ressources synchronisées et en bonne santé : chaque Deployment a alors fini de déployer l'image dont le tag (le SHA du commit, écrit par la CI) est affiché en tête des logs du Job. Amélioration prévue : faire renvoyer ce SHA par la route de version de chaque service (aujourd'hui elle renvoie `app.version` et demande un jeton).

### Où regarder

- **Argo CD** : application `teamslot-staging`. Le Job apparaît comme hook ; une synchronisation dont le test échoue est en échec.
- **kubectl** (accès en lecture au namespace `staging`) :

```bash
kubectl get job smoke-test -n staging
kubectl logs job/smoke-test -n staging
```

Chaque contrôle affiche une ligne `OK` ou `KO`, puis `RÉSULTAT`. Le Job précédent est supprimé au lancement du suivant (`BeforeHookCreation`) : on lit toujours le dernier test.

### Que faire si le test échoue

1. Lire les lignes `KO` dans `kubectl logs job/smoke-test -n staging`.
2. **Santé pas UP** : regarder le service concerné (`kubectl get pods -n staging`, `kubectl logs deploy/<service> -n staging --tail=100`). Causes fréquentes : base de données, Kafka, migration Flyway, variable d'environnement manquante.
3. **401 attendu, autre code reçu** : 🔒 incident de sécurité possible, la vérification du JWT ne protège plus la route. `200` = route ouverte sans authentification ; `404` ou `5xx` = route ou service cassé. Prévenir la référente sécurité, et revenir en arrière si la route est ouverte.
4. **Retour arrière** : `git revert` du commit fautif sur `main` ; Argo CD redéploie la version précédente et relance le test.
5. Noter le constat dans le ticket Jira du changement.

### Tester le script avant de fusionner

Avec l'accès limité au staging : uniquement des objets nommés `postsync-test-…`, sans secret monté, et on ne supprime que les siens. Copier le Job (`smoke-test.yaml`) sous le nom `postsync-test-smoke`, sans les annotations Argo CD, avec une ConfigMap `postsync-test-smoke` créée depuis le script, puis :

```bash
kubectl apply -n staging -f postsync-test-smoke.yaml
kubectl wait --for=condition=complete --timeout=600s job/postsync-test-smoke -n staging
kubectl logs job/postsync-test-smoke -n staging
kubectl delete -n staging -f postsync-test-smoke.yaml
```
