# ADR-007 — Équipes créées et gérées par les joueurs, sans limite d'appartenance

Statut : accepté (Sprint 0)  
Remplace : —

## Contexte
Les groupes d'amis se composent librement, et un même joueur joue souvent avec plusieurs groupes. La plupart des matchs sont des matchs de groupe, sans équipe enregistrée.

## Décision
Tout joueur avec un compte peut créer une équipe, dont il devient capitaine, et appartenir à plusieurs équipes. Aucune limite de taille ni de nombre. Une équipe est facultative pour jouer : un match de groupe avec des invités sans compte reste possible. Une équipe a toujours exactement un capitaine actif : si le capitaine part, le rôle passe au vice-capitaine, puis au membre le plus ancien.

## Conséquences
+ Souple et fidèle à la réalité des groupes d'amis.
+ Aucune validation par un tiers : l'organisation reste entre les joueurs.
- Règles de transfert du capitaine et historique des membres à gérer.
- Si un plafond devient utile, il sera ajouté comme paramètre plus tard.

## Alternatives écartées
- Équipes validées par un administrateur ou un gérant : lourd et peu utile.
- Un joueur dans une seule équipe : contraire aux usages.
- Limite de taille fixe dès le départ : prématuré.
