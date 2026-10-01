# ADR-011 — SonarCloud pour l'analyse du code

Statut : accepté (Sprint 0)  
Remplace : —

## Contexte
La CI doit analyser le code à chaque pull request, avec une porte de qualité qui bloque la fusion (SCRUM-20).

## Décision
SonarCloud, plan Free, gratuit pour un dépôt public.

- Organisation `teamslot-app`, projet `teamslot-app_teamslot`.
- L'analyse automatique de SonarCloud est désactivée : l'analyse est lancée par la CI, avec le jeton stocké dans le secret GitHub `SONAR_TOKEN`.
- Le dépôt est public. Le risque d'y publier un secret est fortement réduit par Gitleaks : hook pre-commit en local et scan obligatoire dans la CI.

## Conséquences
+ Aucun serveur à héberger ni à maintenir.
+ Quality gate bloquant directement dans la CI.
- Dépendance à un service externe.
- Gratuit seulement tant que le dépôt reste public.
- Limites du plan Free : 5 membres, quality gate « Sonar way » par défaut, analyse de la branche `main` et des pull requests seulement.
- Il faudra un projet SonarCloud par service ; aujourd'hui, un seul projet couvre le dépôt.

## Alternatives écartées
- SonarQube Community lancé en conteneur hors du cluster (environ 2 Go de mémoire) : retenu comme solution de repli si le dépôt devient privé.
