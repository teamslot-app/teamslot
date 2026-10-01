# ADR-011 — SonarCloud pour l'analyse du code

Statut : accepté (Sprint 0)  
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
