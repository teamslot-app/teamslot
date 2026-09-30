# Cluster Kubernetes local (k3d)

Le cluster tourne sur le PC d'Amira avec k3d (k3s dans Docker). Il sert d'environnement de staging pour TeamSlot.

## Prérequis

- Docker Desktop démarré (limité à 6 Go de mémoire via `.wslconfig`)
- kubectl, Helm et k3d installés

```bash
kubectl version --client
helm version
k3d version
```

## Création

```bash
k3d cluster create teamslot --agents 1 -p "8080:80@loadbalancer" --api-port 0.0.0.0:6550
kubectl get nodes   # doit afficher 2 noeuds en Ready
```

Création des namespaces :

```bash
for ns in staging prod platform monitoring argocd; do kubectl create namespace $ns; done
kubectl get ns
```

## Démarrage et arrêt

```bash
k3d cluster stop teamslot    # en fin de journée
k3d cluster start teamslot   # pour reprendre
```

## Dépannage

| Problème | Solution |
|---|---|
| `Cannot connect to the Docker daemon` | Démarrer Docker Desktop, puis réessayer |
| `docker run` échoue avec `no such host` | Problème de DNS : ajouter `"dns": ["1.1.1.1", "8.8.8.8"]` dans Docker Desktop → Settings → Docker Engine, puis Apply & restart |
| `kubectl get nodes` ne répond pas | Vérifier que le cluster est démarré : `k3d cluster list` |
| PC lent ou Docker qui plante | Arrêter les conteneurs inutiles ; la mémoire est limitée à 6 Go |

## Accès pour l'équipe

À compléter dans SCRUM-37 (adresse Tailscale, port-forward, créneaux où le cluster est allumé).
