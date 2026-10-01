# ADR-001 — Microservices Spring Boot, une base par service

Statut : accepté (Sprint 0)  
Remplace : —

## Contexte
Le module impose un back-end Spring Boot, et l'un des objectifs d'apprentissage est de construire des microservices communicants. L'atelier de modélisation a dégagé neuf domaines métier : identity, venue, team, match, payment, messaging, notification, search et review.

## Décision
Un microservice Spring Boot 3 (Java 21) par domaine. Chaque service possède ses données : une base PostgreSQL par service, et aucun service ne lit la base d'un autre. À l'intérieur, chaque service suit la même architecture hexagonale.

## Conséquences
+ Frontières claires : chaque service a une responsabilité et ses propres données.
+ Déploiement et mise à l'échelle indépendants ; les quatre membres travaillent en parallèle sur des services différents.
+ Un service peut évoluer sans toucher aux autres, grâce aux contrats OpenAPI et aux événements.
- Plus de complexité : réseau, cohérence des données, observabilité.
- Consommation de mémoire plus élevée sur des PC personnels de 16 Go.

## Alternatives écartées
- Monolithe modulaire : plus simple, mais ne répond pas à l'objectif d'apprentissage.
- Base de données partagée entre services : couplage fort, contraire à l'indépendance recherchée.
