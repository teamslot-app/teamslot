# ADR-008 — Kustomize pour nos manifestes, Helm pour les outils tiers

Statut : accepté (Sprint 0)  
Remplace : —

## Contexte
Il faut décrire le déploiement de nos services sur Kubernetes, et installer des outils tiers : Argo CD, Prometheus, Grafana et Loki.

## Décision
Kustomize (base et overlays staging et prod) pour nos propres manifestes. Helm seulement pour installer les outils tiers, à partir de leurs charts officiels.

**Exception :** PostgreSQL est déployé avec Kustomize et l'image officielle `postgres`, pas avec un chart Helm (voir ADR-014).

## Conséquences
+ Nos manifestes restent lisibles : pas de templates à apprendre pour nos propres services.
+ Les charts officiels évitent de réécrire des installations complexes.
- Deux outils à connaître au lieu d'un.

## Alternatives écartées
- Helm partout : des templates plus lourds pour très peu de variations entre environnements.
- Manifestes bruts dupliqués par environnement : duplication et risques d'écart.
