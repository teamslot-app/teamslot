# ADR-002 — Communication par événements Kafka (saga chorégraphiée)

Statut : accepté (Sprint 0)  
Remplace : —

## Contexte
Plusieurs services doivent réagir à un même fait : une réservation confirmée crée un match, déclenche un paiement, une notification et la mise à jour de l'index de recherche.

## Décision
Les services communiquent par événements Kafka : topics nommés domaine.événement, clé = identifiant de l'agrégat, enveloppe commune, ajout de champs uniquement (jamais de suppression). Les appels REST synchrones sont réservés aux réponses immédiates (réserver un créneau, calculer un prix). La saga est chorégraphiée : chaque service réagit aux événements des autres, sans orchestrateur central. La publication passe par une outbox et les consommateurs sont idempotents.

## Conséquences
+ Faible couplage : une panne de team-service n'empêche pas de jouer un match, grâce aux vues locales.
+ Extension simple : un nouveau service s'abonne à des événements existants sans modifier les autres.
- Cohérence à terme, et non immédiate.
- Débogage plus difficile : il faut suivre un identifiant de corrélation d'un service à l'autre.
- Kafka est un composant de plus à exploiter (un broker en mode KRaft).

## Alternatives écartées
- REST synchrone entre tous les services : couplage temporel, une panne se propage.
- Orchestrateur central pour la saga : point unique de défaillance et plus lourd à écrire.
- Une autre file de messages (RabbitMQ) : non retenue, Kafka fait partie des objectifs d'apprentissage.
