# ADR-005 — AWS Always Free, en fin de projet seulement

Statut : accepté (Sprint 0)  
Remplace : version 1.0 du guide de référence (AWS Always Free sans échéance)

## Contexte
Le budget est nul, AWS n'est pas nécessaire pour démarrer, et un compte cloud mal sécurisé expose à des coûts imprévus. L'intégration AWS est suivie dans l'epic SCRUM-39.

## Décision
On utilise uniquement les services de l'offre AWS Always Free, et seulement en fin de projet : DynamoDB, SNS/SQS, Lambda, IAM, avec GitHub Actions connecté par OIDC. Pendant le développement, on travaille avec DynamoDB Local et LocalStack.

## Conséquences
+ Aucun coût, et aucun risque de facturation pendant le développement.
+ L'architecture hexagonale isole AWS dans des adaptateurs : passer du local à AWS ne touche pas au métier.
- L'intégration AWS arrive tard : le risque est reporté, pas supprimé.
- DynamoDB Local et LocalStack ne reproduisent pas tout à fait le comportement réel d'AWS.

## Alternatives écartées
- AWS dès le Sprint 0 : comptes à sécuriser et risque de facturation avant d'en avoir besoin.
- Tout en local, sans AWS : on perdrait l'objectif d'apprentissage du cloud.
