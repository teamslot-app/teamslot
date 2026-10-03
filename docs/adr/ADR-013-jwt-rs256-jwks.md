# ADR-013 — Jetons JWT signés en RS256, clé publique publiée en JWKS

Statut : accepté (Sprint 1)  
Remplace : —

## Contexte
La passerelle doit vérifier chaque jeton (SCRUM-67), et la conception demande que chaque service le revérifie aussi (défense en profondeur, section 11.5). Avec une signature à secret partagé (HS256), ce secret devrait être copié dans la passerelle et les neuf services : chacun de ces endroits permettrait de fabriquer de faux jetons (menace d'usurpation, modèle de menaces).

## Décision
identity-service signe les jetons en RS256 avec une clé privée qu'il est seul à détenir (secret scellé dans le cluster). Il publie la clé publique sur `GET /.well-known/jwks.json` (contrat identity.yaml). La passerelle et les services vérifient avec cette clé publique : signature, expiration et émetteur (`iss = teamslot-identity`). Seul l'algorithme RS256 est accepté, ce qui refuse aussi les jetons « alg: none ».

Jeton d'accès de 15 minutes ; claims : `sub` (identifiant de l'utilisateur), `iss`, `iat`, `exp`. La passerelle transmet le jeton tel quel aux services ; elle supprime tout en-tête `X-User-Id` envoyé par le client.

## Conséquences
+ La clé privée ne quitte jamais identity-service : une passerelle ou un service compromis ne peut pas fabriquer de jetons.
+ La clé publique se distribue sans risque, et chaque service vérifie le jeton sans appeler identity.
+ Changer de clé est possible sans coupure : publier la nouvelle clé dans le JWKS avant de l'utiliser (champ `kid`).
- La signature RSA est un peu plus coûteuse que HS256 (négligeable à notre échelle).
- La passerelle et les services dépendent du JWKS au premier jeton reçu : identity doit être joignable à ce moment-là (la clé est ensuite mise en cache).

## Alternatives écartées
- HS256 avec un secret partagé : secret copié dans dix composants, chacun pouvant fabriquer des jetons.
- Vérification par appel à identity à chaque requête (jetons opaques) : une requête réseau de plus pour chaque appel d'API, et identity devient un point de passage obligé.
