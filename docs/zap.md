# Scan OWASP ZAP du staging (SCRUM-72)

## Ce qui se passe

Le workflow `.github/workflows/zap-staging.yml` lance un scan ZAP **baseline** (passif) contre le staging, **chaque soir** (19 h 47 UTC) et à la main (onglet Actions, `zap-staging`, « Run workflow ») :

1. le runner GitHub rejoint le réseau Tailscale avec l'étiquette `tag:ci` (client OAuth créé par Amira, secrets `TS_OAUTH_CLIENT_ID` et `TS_OAUTH_SECRET`). L'ACL Tailscale n'autorise `tag:ci` que vers le PC d'Amira, port 8080 ;
2. le job vérifie que le staging répond (présence de la cible dans le réseau, tunnel, puis jusqu'à 6 essais HTTP espacés de 10 s) ; s'il ne répond pas (PC éteint), il affiche un avertissement et s'arrête **sans échouer**. Les logs ne montrent aucune adresse IP publique ;
3. ZAP fait deux scans, **sans envoyer d'attaque** : le **front** `http://100.89.116.27:8080/` (Ingress `/`, SCRUM-114 : pages HTML et en-têtes de nginx) et la **passerelle** `http://100.89.116.27:8080/api/v1/template/version` (Ingress `/api` : réponses JSON et 401) ;
4. une alerte de niveau **élevé** (High) non acceptée dans `.zap/rules.tsv` fait **échouer** le job ;
5. le rapport (HTML, JSON, Markdown) est gardé 30 jours en artefact du run, et le résumé s'affiche dans le Summary.

## Garde-fous (critères de sécurité du ticket)

- **Cible fixe, staging uniquement** : l'adresse est écrite dans le workflow ; l'Ingress n'existe que dans `deploy/k8s/overlays/staging`.
- **Jamais sur une PR** : déclenchement `schedule` et `workflow_dispatch` seulement, donc impossible depuis un fork, et les secrets ne sortent pas de la branche par défaut.
- **Passif seulement** : un scan actif (qui envoie des attaques et crée des données) n'est pas prévu dans ce sprint ; s'il est ajouté, il reste limité au staging.
- **Pas d'authentification pour l'instant** : le scan voit les routes publiques et les réponses 401 des routes protégées. S'il s'authentifie plus tard : compte de test dédié au staging, sans droits gérant, identifiants dans les secrets GitHub, jeton jamais dans les logs ni dans le rapport.
- **Image ZAP épinglée** par version et par empreinte (`2.16.1@sha256:…`).

## Avant de lancer un scan à la main

Prévenir Amira : son PC et Docker Desktop doivent être allumés, et **pas sur un Wi-Fi public**. Pendant le scan, ZAP interroge le port 8080 de son PC ; ce port étant lié à toutes les interfaces (voir « Limites connues »), il est aussi visible du réseau local où se trouve le PC. Le scan programmé du soir suppose les mêmes conditions (voir `docs/cluster.md`) ; GitHub peut le retarder de plusieurs heures.

## Lire le rapport

Page du run, rubrique **Artifacts**, `zap-rapport-<n>`, puis ouvrir `rapport-web.html` (front) et `rapport-api.html` (passerelle). Chaque alerte donne la règle (id), le niveau (High, Medium, Low, Informational), l'URL et une explication.

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

- Le front est une application React : le robot d'exploration classique de ZAP ne lance pas le JavaScript, donc il voit surtout la page d'accueil et ses ressources. Piste : l'« AJAX spider » (navigateur intégré à ZAP), plus lent.
- Une API JSON offre peu de liens à suivre : le scan de la passerelle couvre surtout les en-têtes et les réponses. Piste pour plus tard : un scan d'API passif à partir des contrats `docs/api/*.yaml`.
- 🔒 Le cluster expose le port 8080 sur **toutes** les interfaces du PC d'Amira (`k3d … -p "8080:80@loadbalancer"`), donc aussi sur son réseau local, pas seulement sur Tailscale. Piste : lier le port à l'adresse Tailscale à la création du cluster. Point ajouté au modèle de menaces.
