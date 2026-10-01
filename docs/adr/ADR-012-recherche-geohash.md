# ADR-012 — Recherche géographique par geohash dans DynamoDB

Statut : accepté (Sprint 0)  
Remplace : —

## Contexte
La carte, la recherche de terrains et de matchs ouverts par proximité, et le mode « jouer maintenant » exigent des requêtes par position.

## Décision
search-service indexe les terrains et les matchs avec un geohash dans DynamoDB (DynamoDB Local pendant le développement, AWS Always Free en fin de projet). La recherche passe par l'interface NearbySearch, ce qui garde PostGIS possible comme solution de repli sans toucher au métier.

## Conséquences
+ Reste dans l'offre gratuite d'AWS et fait pratiquer DynamoDB.
+ L'interface NearbySearch est un point d'extension : changer de technologie ne modifie pas le code existant.
- Les requêtes de proximité sont moins naturelles qu'avec PostGIS : il faut interroger les cellules voisines.

## Alternatives écartées
- PostGIS dès le départ : requêtes géographiques plus simples, mais sans l'objectif d'apprentissage DynamoDB ; conservé en repli.
