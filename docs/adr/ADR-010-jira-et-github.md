# ADR-010 — Jira pour le backlog, GitHub pour le code et la CI

Statut : accepté (Sprint 0)  
Remplace : version 1.0 du guide de référence (GitHub Projects pour le backlog)

## Contexte
Il faut un lieu pour le code, la CI et le registre d'images, et un outil de backlog Scrum avec sprints, tableau et points.

## Décision
GitHub pour le code, Actions et le registre GHCR ; connexion à AWS par OIDC en fin de projet. Jira (espace teamslot-app) pour le backlog et les sprints, relié à GitHub par l'application GitHub for Atlassian : la clé du ticket (SCRUM-XX) figure dans les branches, les commits et les pull requests.

## Conséquences
+ Outils standard du métier, avec une traçabilité du ticket jusqu'à la pull request.
+ Le tableau Jira sert directement aux rituels Scrum : daily, Sprint Review, rétrospective.
- Deux outils à garder cohérents.
- La connexion Jira–GitHub exige un administrateur de l'espace Atlassian.

## Alternatives écartées
- GitHub Projects pour le backlog : prévu à l'origine, remplacé par Jira.
