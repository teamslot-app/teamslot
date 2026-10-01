# ADR-004 — GitHub Actions, GHCR et Argo CD (GitOps)

Statut : accepté (Sprint 0)  
Remplace : —

## Contexte
Il faut construire, tester, analyser et déployer automatiquement, sans intervention manuelle sur le cluster.

## Décision
GitHub Actions pour la CI, GHCR pour les images, Argo CD pour le déploiement : l'état du cluster est décrit dans Git, et Argo CD l'applique.
Les images publiées sur GHCR sont **publiques**.

## Conséquences
+ Tout est gratuit et bien documenté.
+ Le code est déjà sur GitHub : pas d'outil supplémentaire à relier.
+ Chaque déploiement est tracé dans Git et peut être annulé par un retour arrière.
+ Les images étant publiques, le cluster peut les récupérer sans secret d'accès au registre.
- Tout le monde peut télécharger les images : aucune donnée sensible ne doit y être embarquée.
- Il faut un cluster accessible pour Argo CD (voir ADR-003).

## Point ouvert
L'emplacement des manifestes suivis par Argo CD reste à décider avec Amira pour SCRUM-36 : dossier `deploy/` dans ce dépôt, ou dépôt séparé.

## Alternatives écartées
- GitLab CI : le code est déjà sur GitHub.
