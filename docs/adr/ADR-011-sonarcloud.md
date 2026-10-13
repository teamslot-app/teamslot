# ADR-011 — SonarCloud pour l'analyse du code

Statut : accepté (Sprint 0), complété au Sprint 1 (SCRUM-86)  
Remplace : —

## Contexte
La CI doit analyser le code à chaque pull request, avec une porte de qualité qui bloque la fusion (SCRUM-20).

## Décision
SonarCloud, gratuit pour un dépôt public. Le dépôt est public : cela n'est sans risque que si aucun secret n'y est jamais commité, ce que Gitleaks garantit.

## Conséquences
+ Aucun serveur à héberger ni à maintenir.
+ Quality gate bloquant directement dans la CI.
- Dépendance à un service externe.
- Gratuit seulement tant que le dépôt reste public.

## Alternatives écartées
- SonarQube Community lancé en conteneur hors du cluster (environ 2 Go de mémoire) : retenu comme solution de repli si le dépôt devient privé.

## Complément (Sprint 1, SCRUM-86) : un projet SonarCloud par service
Le dépôt est un monorepo. **Chaque service a son propre projet SonarCloud**, lié au même dépôt GitHub (mode monorepo de SonarQube Cloud), avec la clé `teamslot-app_<service>` (exemple : `teamslot-app_match-service`). L'appelant CI du service la passe au workflow réutilisable par l'input `sonar_project_key`. La clé `teamslot-app_teamslot` reste celle du service modèle.

Constat qui a motivé ce complément : avec une seule clé partagée, chaque analyse de `main` écrasait celle du service précédent (le projet du modèle a même été renommé « payment-service »), et l'analyse d'une pull request comparait le code avec celui d'un autre service.

Conséquences :
- Créer le projet SonarCloud **avant** d'ajouter un nouveau service à la CI, sinon l'analyse échoue (« Project not found »). En monorepo, l'analyse automatique n'est pas disponible : seule la CI analyse.
- La porte de qualité bloque réellement la fusion depuis SCRUM-86 : `build-test` échoue si la porte échoue (`sonar.qualitygate.wait=true`), et `ci-gate` bloque la fusion dès qu'un check est en échec.
