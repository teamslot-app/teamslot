# ADR-003 — Cluster local k3d, accès par Tailscale

Statut : accepté (Sprint 0)  
Remplace : version 1.0 du guide de référence (k3s sur une machine locale ou partagée)

## Contexte
Pas de serveur ni de budget cloud ; des PC personnels de 16 Go.

## Décision
k3d (k3s dans Docker) sur le PC d'Amira, avec 6 Go réservés à Docker. L'équipe accède à Grafana et à Argo CD par Tailscale.

## Conséquences
+ Gratuit, même Kubernetes que k3s, démarrage et arrêt à la demande.
- Le cluster n'est disponible que lorsque le PC est allumé.

## Alternatives écartées
- k3s sur Ubuntu : demande Linux, alors que l'équipe travaille sous Windows.
- Serveur cloud : coût et création de compte.
