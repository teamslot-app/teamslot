# ADR-004 — GitHub Actions, GHCR et Argo CD (GitOps)

Statut : accepté (Sprint 0)  
Remplace : —

## Contexte
Il faut construire, tester, analyser et déployer automatiquement, sans intervention manuelle sur le cluster.

## Décision
GitHub Actions pour la CI, GHCR pour les images, Argo CD pour le déploiement : l'état du cluster est décrit dans Git, et Argo CD l'applique.

## Conséquences
+ Tout est gratuit et bien documenté.
+ Le code est déjà sur GitHub : pas d'outil supplémentaire à relier.
+ Chaque déploiement est tracé dans Git et peut être annulé par un retour arrière.
- Il faut un cluster accessible pour Argo CD (voir ADR-003).

## Alternatives écartées
- GitLab CI : le code est déjà sur GitHub.
