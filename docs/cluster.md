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

## Accès pour l'équipe (Tailscale)

Le cluster tourne sur le PC d'Amira. L'accès se fait via Tailscale (réseau privé, rien n'est exposé sur Internet).

- Adresse Tailscale du PC : `100.x.y.z` (remplacer par la vraie adresse)
- Prérequis : Tailscale installé et connecté, invitation de partage acceptée

Commandes lancées par Amira (terminaux à laisser ouverts) :

```bash
kubectl port-forward --address 0.0.0.0 svc/monitoring-grafana -n monitoring 3001:80
kubectl port-forward --address 0.0.0.0 svc/argocd-server -n argocd 8443:443
```

Côté équipe :
- Grafana : http://100.x.y.z:3001
- Argo CD : https://100.x.y.z:8443

Le cluster n'est accessible que lorsque le PC d'Amira est allumé et le cluster démarré.
Créneaux où le cluster est allumé : à compléter.

## Supervision (Prometheus et Grafana)

Installation :
```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm install monitoring prometheus-community/kube-prometheus-stack -n monitoring --set alertmanager.enabled=false --set prometheus.prometheusSpec.retention=2d
```

Accès à Grafana (utilisateur `admin`, mot de passe : voir le secret `monitoring-grafana`, jamais dans Git) :
```bash
kubectl port-forward svc/monitoring-grafana -n monitoring 3001:80
```

Tableau de bord : `deploy/monitoring/dashboard-teamslot.json` (import via Dashboards → New → Import).
