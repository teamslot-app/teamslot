# ADR-004 — GitHub Actions, GHCR et Argo CD (GitOps)

Statut : accepté (Sprint 0), complété au Sprint 1  
Remplace : —

## Contexte
Il faut construire, tester, analyser et déployer automatiquement, sans intervention manuelle sur le cluster.

## Décision
GitHub Actions pour la CI, GHCR pour les images, Argo CD pour le déploiement : l'état du cluster est décrit dans Git, et Argo CD l'applique.
Les images publiées sur GHCR sont **publiques**.

**Emplacement des manifestes (décidé au Sprint 1, SCRUM-36) :** les manifestes restent dans ce dépôt, dossier `deploy/`.

**Mise à jour des images :** après la fusion d'une PR dans `main`, la CI publie l'image puis un bot commite le nouveau tag dans l'overlay de staging. La protection de `main` est contournée **pour ce seul bot**, avec ces garde-fous :
- le contournement est accordé à une identité dédiée (GitHub App ou jeton à portée limitée), jamais à tous les administrateurs ni au `GITHUB_TOKEN` de n'importe quel workflow ;
- le job qui commite ne tourne que sur `push` vers `main`, donc après relecture et fusion, jamais sur une pull request ;
- le job vérifie que son commit ne modifie que `deploy/k8s/overlays/staging/kustomization.yaml` ; sinon il échoue ;
- le commit porte un message reconnaissable (`chore(deploy): …`) et ne relance pas la CI, pour éviter les boucles ;
- la promotion vers l'overlay de production passe toujours par une pull request relue.

## Conséquences
+ Tout est gratuit et bien documenté.
+ Le code est déjà sur GitHub : pas d'outil supplémentaire à relier.
+ Chaque déploiement est tracé dans Git et peut être annulé par un retour arrière.
+ Les images étant publiques, le cluster peut les récupérer sans secret d'accès au registre.
+ Code et manifestes dans un seul dépôt : une seule PR peut modifier un service et son déploiement.
- Tout le monde peut télécharger les images : aucune donnée sensible ne doit y être embarquée.
- Il faut un cluster accessible pour Argo CD (voir ADR-003).
- Le contournement de la protection de `main` est une exception : si l'identité du bot fuitait, elle permettrait d'écrire dans `main` sans relecture. D'où une identité dédiée, à portée limitée, et la vérification du fichier modifié.

## Alternatives écartées
- GitLab CI : le code est déjà sur GitHub.
- Dépôt séparé pour les manifestes : plus d'isolement, mais deux dépôts à synchroniser pour chaque changement ; écarté pour une équipe de quatre.
- Argo CD Image Updater : un composant de plus dans le cluster, à budget mémoire serré.
- Pull request automatique pour chaque tag : aucun contournement nécessaire, mais une PR à fusionner à chaque déploiement ; possible plus tard si l'équipe préfère.
