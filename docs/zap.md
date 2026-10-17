# Scan OWASP ZAP du staging (SCRUM-72)

## Ce qui se passe

Le workflow `.github/workflows/zap-staging.yml` lance un scan ZAP **baseline** (passif) contre le staging, **chaque soir** (19 h 47 UTC) et à la main (onglet Actions, `zap-staging`, « Run workflow ») :

1. le runner GitHub rejoint le réseau Tailscale avec l'étiquette `tag:ci` (client OAuth créé par Amira, secrets `TS_OAUTH_CLIENT_ID` et `TS_OAUTH_SECRET`). L'ACL Tailscale n'autorise `tag:ci` que vers le PC d'Amira, port 8080 ;
2. si le staging ne répond pas (PC éteint), le job affiche un avertissement et s'arrête **sans échouer** ;
3. ZAP visite la cible `http://100.89.116.27:8080/api/v1/template/version` (Ingress `/api` vers la passerelle) et analyse les réponses, **sans envoyer d'attaque** ;
4. une alerte de niveau **élevé** (High) non acceptée dans `.zap/rules.tsv` fait **échouer** le job ;
5. le rapport (HTML, JSON, Markdown) est gardé 30 jours en artefact du run, et le résumé s'affiche dans le Summary.

## Garde-fous (critères de sécurité du ticket)

- **Cible fixe, staging uniquement** : l'adresse est écrite dans le workflow ; l'Ingress n'existe que dans `deploy/k8s/overlays/staging`.
- **Jamais sur une PR** : déclenchement `schedule` et `workflow_dispatch` seulement, donc impossible depuis un fork, et les secrets ne sortent pas de la branche par défaut.
- **Passif seulement** : un scan actif (qui envoie des attaques et crée des données) n'est pas prévu dans ce sprint ; s'il est ajouté, il reste limité au staging.
- **Pas d'authentification pour l'instant** : le scan voit les routes publiques et les réponses 401 des routes protégées. S'il s'authentifie plus tard : compte de test dédié au staging, sans droits gérant, identifiants dans les secrets GitHub, jeton jamais dans les logs ni dans le rapport.
- **Image ZAP épinglée** par version et par empreinte (`2.16.1@sha256:…`).

## Lire le rapport

Page du run, rubrique **Artifacts**, `zap-rapport-<n>`, puis ouvrir `rapport.html`. Chaque alerte donne la règle (id), le niveau (High, Medium, Low, Informational), l'URL et une explication.

## Accepter un faux positif

Ajouter une ligne dans `.zap/rules.tsv`, **séparée par des tabulations**, avec une justification d'une ligne :

```
10021	IGNORE	En-tête X-Content-Type-Options ajouté par Traefik en staging, absent seulement sur la réponse 404 de Traefik
```

Puis PR relue, comme une acceptation Sonar. Une ligne sans justification est refusée en relecture.

## Limites connues

- Une API JSON offre peu de liens à suivre : le scan baseline couvre surtout les en-têtes et les réponses de la passerelle. Piste pour plus tard : un scan d'API passif à partir des contrats `docs/api/*.yaml`.
- 🔒 Le cluster expose le port 8080 sur **toutes** les interfaces du PC d'Amira (`k3d … -p "8080:80@loadbalancer"`), donc aussi sur son réseau local, pas seulement sur Tailscale. Piste : lier le port à l'adresse Tailscale à la création du cluster. Point ajouté au modèle de menaces.
