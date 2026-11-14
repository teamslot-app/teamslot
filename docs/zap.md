# Scan OWASP ZAP du staging (SCRUM-72, SCRUM-129)

## Ce qui se passe

Le workflow `.github/workflows/zap-staging.yml` lance un scan ZAP **passif et authentifié** (SCRUM-129) contre le staging, **chaque soir** (19 h 47 UTC) et à la main (onglet Actions, `zap-staging`, « Run workflow ») :

1. le runner GitHub rejoint le réseau Tailscale avec l'étiquette `tag:ci` (client OAuth créé par Amira, secrets `TS_OAUTH_CLIENT_ID` et `TS_OAUTH_SECRET`). L'ACL Tailscale n'autorise `tag:ci` que vers le PC d'Amira, **port 8443** ;
2. le job vérifie que le staging répond (`https://amira.tail764250.ts.net:8443`, HTTPS de SCRUM-34, jusqu'à 6 essais espacés de 10 s) ; s'il ne répond pas (PC éteint), il affiche un avertissement et s'arrête **sans échouer**. Les logs ne montrent aucune adresse IP ;
3. il **connecte le compte de test dédié** au scan (`.zap/compte_test.py`), en le créant au premier passage. Le jeton est masqué dans les logs et gardé dans un fichier temporaire du runner (droits 600), effacé à la fin du job ;
4. il construit la liste des **routes `GET`** des contrats `docs/api/*.yaml` des services déployés (`.zap/plan.py`), puis vérifie avec le jeton qu'au moins une **route protégée répond 200**. Sinon, le job échoue : le scan ne serait pas authentifié. Les codes HTTP de chaque route sont affichés dans le Summary ;
5. ZAP 2.17.0 (image `.zap/Dockerfile`) exécute le plan (Automation Framework, `zap/plan.json`) :
   - les routes `GET` des contrats, avec le jeton ;
   - le robot classique (pages HTML) ;
   - l'**AJAX spider** (Firefox sans écran, 3 min au plus) pour le front React ;
   - l'analyse passive des réponses ;
6. le rapport est vérifié : s'il contient le jeton ou le mot de passe, il **n'est pas publié** et le job échoue ;
7. une alerte de niveau **élevé** (High) non acceptée dans `.zap/rules.tsv` fait **échouer** le job ;
8. le rapport (HTML, JSON, Markdown) est gardé 30 jours en artefact du run, et le résumé s'affiche dans le Summary.

## Garde-fous (critères de sécurité des tickets SCRUM-72 et SCRUM-129)

- **Cible fixe, staging uniquement** : l'adresse est écrite dans le workflow ; l'Ingress n'existe que dans `deploy/k8s/overlays/staging`.
- **Jamais sur une PR** : déclenchement `schedule` et `workflow_dispatch` seulement, donc impossible depuis un fork, et les secrets ne sortent pas de la branche par défaut.
- **Passif, et `GET` seulement** : ZAP n'envoie **aucune attaque**. Il ne fait que des requêtes de lecture : les routes `GET` des contrats, et les pages suivies par les robots. Le robot classique n'envoie aucun formulaire (`postForm: false`). L'AJAX spider ne suit **que les liens** : aucun bouton cliqué, aucun champ rempli (`clickDefaultElems: false`, `elements: [a]`, `randomInputs: false`). Le scan ne crée, ne modifie ni ne supprime donc **aucune donnée** : pas de match, pas de paiement, pas de compte (sauf le compte de test lui-même, au premier passage).
- **Compte de test dédié** au staging, sans droits gérant (inscription normale de joueur). Le scan n'agit que sur ses propres données, et seulement en lecture : il n'y a **rien à réinitialiser** après le scan. Son numéro et son mot de passe sont dans les secrets GitHub `ZAP_TEST_PHONE` et `ZAP_TEST_PASSWORD`, jamais dans le dépôt.
- **Aucun secret dans les logs ni dans le rapport** :
  - mot de passe, numéro et jeton sont masqués (`::add-mask::`) ;
  - curl lit le jeton dans un fichier (`-H @fichier`), et ZAP le reçoit par variable d'environnement (`-e` sans valeur) : jamais sur une ligne de commande ;
  - ZAP n'ajoute le jeton qu'aux requêtes vers l'hôte du staging (`ZAP_AUTH_HEADER_SITE`) ;
  - le rapport est vérifié avant d'être publié.
- **Image ZAP épinglée** par version et par empreinte (`2.17.0@sha256:…`) dans `.zap/Dockerfile`, que **Dependabot** suit (écosystème `docker`, dossier `/.zap`).

## Mettre en place (une seule fois)

1. **Amira** : dans l'ACL Tailscale, autoriser `tag:ci` vers son PC sur le port **8443** (au lieu de 8080).
2. **Sara** : créer les deux secrets du dépôt (GitHub, Settings, Secrets and variables, Actions), **sans jamais les écrire ailleurs** (ni chat, ni fichier, ni ticket) :
   - `ZAP_TEST_PHONE` : un numéro de test au format `+2126XXXXXXXX`, qui n'appartient à personne ;
   - `ZAP_TEST_PASSWORD` : 12 caractères au moins, 3 familles parmi minuscules, majuscules, chiffres et symboles, sans le numéro (règles d'identity-service, SCRUM-132).

## Avant de lancer un scan à la main

Prévenir Amira : son PC et Docker Desktop doivent être allumés, et **pas sur un Wi-Fi public**. Pendant le scan, ZAP interroge le port 8443 de son PC. Le scan programmé du soir suppose les mêmes conditions (voir `docs/cluster.md`) ; GitHub peut le retarder de plusieurs heures.

## Changer les routes scannées

`.zap/plan.py` lit les contrats : une nouvelle route `GET` est scannée **automatiquement**. Un nouveau service déployé s'ajoute dans la liste `DEPLOYES`. Une route dont un paramètre de chemin est inconnu (`{id}` d'un match, par exemple) est ignorée, sauf si une valeur de démo est donnée dans `CONNUS`. Pour voir la liste sans rien lancer :

```bash
python .zap/plan.py --lister
```

## Lire le rapport

Page du run, rubrique **Artifacts**, `zap-rapport-<n>`, puis ouvrir `rapport.html` (front et passerelle dans le même rapport). Chaque alerte donne la règle (id), le niveau (High, Medium, Low, Informational), l'URL et une explication.

## Accepter un faux positif

Ajouter une ligne dans `.zap/rules.tsv`, **séparée par des tabulations**, avec une justification d'une ligne :

```
10021	IGNORE	En-tête X-Content-Type-Options ajouté par Traefik en staging, absent seulement sur la réponse 404 de Traefik
```

Puis PR relue, comme une acceptation Sonar. Une ligne sans justification est refusée en relecture.

## Premier scan réel (08/11)

Aucune alerte High (job vert). Les alertes sont les mêmes sur le front et la passerelle (le robot remonte jusqu'à la racine du site) :

| Alerte | Niveau | Cause |
|---|---|---|
| CSP : directive sans repli (`form-action`) | Medium | La CSP de nginx (front) ne définit pas `form-action` |
| Isolation insuffisante contre Spectre (COOP, COEP, CORP) | Low | En-têtes `Cross-Origin-*-Policy` absents |
| En-tête `Permissions-Policy` absent | Low | nginx (front) |
| Version du serveur dans l'en-tête `Server` (`nginx/1.31.7`) | Low | `server_tokens` actif |
| ZAP n'est pas à jour (2.17.0 disponible) | Low | Image épinglée en 2.16.1 |
| Commentaires suspects, application moderne, contenu en cache | Informational | Faux positif (code React minifié), robot classique, cache par défaut |

Corrections à planifier (en-têtes nginx du front, mise à jour de l'image ZAP) : hors du périmètre de ce ticket.

## Limites connues

- L'AJAX spider n'est **pas connecté** : le front garde le jeton dans la session du navigateur, que ZAP ne remplit pas. Il voit les pages publiques du front ; les routes protégées sont couvertes par les `GET` des contrats.
- Seules les routes `GET` sont scannées : les failles propres aux `POST` (saisie, droits d'écriture) relèvent des tests d'intégration et de l'E2E, ou d'un futur scan actif limité au staging.
- 🔒 Le cluster expose le port 8080 sur **toutes** les interfaces du PC d'Amira (`k3d … -p "8080:80@loadbalancer"`), donc aussi sur son réseau local, pas seulement sur Tailscale. Piste : lier le port à l'adresse Tailscale à la création du cluster. Point ajouté au modèle de menaces.
