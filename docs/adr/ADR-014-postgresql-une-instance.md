# ADR-014 — Une instance PostgreSQL, quatre bases isolées par les droits

Statut : proposé (exception à ADR-008 validée par Laila)

## Contexte
ADR-001 impose une base par service. Docker dispose de 6 Go (ADR-003). ADR-008 prévoit Helm pour les outils tiers, mais il n'existe pas de chart officiel du projet PostgreSQL, et depuis août 2025 les images Bitnami gratuites sont passées dans bitnamilegacy, sans mises à jour de sécurité, ce qui contredit notre politique Trivy et Dependabot.

## Décision
Une instance PostgreSQL (image officielle `postgres`, manifeste Kustomize), quatre bases (identity, venue, match, payment) et une base `template` pour le service modèle. Un utilisateur ordinaire par base, sans droit sur les autres (REVOKE CONNECT FROM PUBLIC). L'instance est amorcée par un compte administrateur dédié, qu'aucun service n'utilise. Volume persistant, mots de passe en secrets scellés (Sealed Secrets).

## Conséquences
- Positif : mémoire très inférieure à quatre instances ; isolement garanti par les droits (frontière TB2) ; image maintenue et analysée par Trivy.
- Négatif : une panne de l'instance touche tous les services ; pas de limite de ressources par base ; sauvegardes et mises à jour à gérer nous-mêmes.

## Alternatives écartées
Quatre instances (trop de mémoire) ; base partagée (contraire à ADR-001) ; chart Bitnami (voir contexte). CloudNativePG pourra être étudié plus tard.
