# ADR-006 — Paiement par un responsable, en ligne ou sur place

Statut : accepté (Sprint 0)  
Remplace : —

## Contexte
Sur les terrains de proximité, un joueur paie aujourd'hui le gérant à l'arrivée.

## Décision
Un responsable paie la totalité, en ligne (simulé) ou sur place. Le gérant confirme l'encaissement dans l'application. Le montant est toujours recalculé par le serveur.

## Conséquences
+ Fidèle aux habitudes des joueurs.
+ Pas de paiement partagé à gérer.
- Risque d'absence du groupe : compensé par le score de fiabilité.

## Alternatives écartées
- Paiement partagé entre joueurs : plus complexe, et peu utilisé sur place.
