# ADR-009 — Front-end React, TypeScript et Vite, mobile préparé

Statut : accepté (Sprint 0)  
Remplace : version 1.0 du guide de référence (framework à décider)

## Contexte
L'application web est prioritaire, le mobile est souhaité ensuite, et le choix du framework front-end est libre (Angular, React, Vue.js…).

## Décision
React + TypeScript + Vite, avec Tailwind CSS, Leaflet (OpenStreetMap), TanStack Query et STOMP pour le chat. Le client TypeScript est généré depuis les contrats OpenAPI. Le mobile vient en trois temps : application web, puis PWA installable, puis React Native (Expo) avec le code commun dans packages/shared.

## Conséquences
+ Standard de l'industrie, très bien outillé pour les tests (Vitest, Playwright) et les scans de sécurité.
+ Le code commun est partagé avec le futur mobile : les écrans ne contiennent pas de logique métier.
- React Native est à apprendre plus tard.

## Alternatives écartées
- Angular ou Vue.js : moins de code partageable avec un futur mobile.
- Flutter : une seule base de code, mais un rendu web moins adapté à une application web prioritaire.
