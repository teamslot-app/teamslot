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
kubectl port-forward --address 0.0.0.0 svc/argocd-server -n argocd 8444:443
```

Côté équipe :
- Grafana : http://100.x.y.z:3001
- Argo CD : https://100.x.y.z:8444

Le cluster n'est accessible que lorsque le PC d'Amira est allumé et le cluster démarré.
Créneaux où le cluster est allumé : à compléter.

## HTTPS du staging (SCRUM-34)

Le staging est joignable en HTTPS sur `https://amira.tail764250.ts.net:8443`, avec un certificat Let's Encrypt émis par Tailscale (nom en `*.ts.net`).

### Ce qui est en place

- Secret scellé `staging-tls` (type `kubernetes.io/tls`) : `deploy/k8s/overlays/staging/sealed-secrets/staging-tls.yaml`.
- Section `tls` sur les Ingress `api` et `web` (classe `traefik`, sans `host` : l'HTTP sur le port 8080 continue de fonctionner).
- Port 8443 du load balancer k3d lié à l'adresse Tailscale, publié sans recréer le cluster :

        k3d cluster edit teamslot --port-add "$(tailscale ip -4):8443:443@loadbalancer"

  Si le cluster est recréé, ajouter `-p "$(tailscale ip -4):8443:443@loadbalancer"` à la commande de la section « Création ».

### Expiration et renouvellement

- Le certificat est valable jusqu'au **12 février 2027**. Rappel au calendrier : **12 janvier 2027**.
- Lire la date de fin (certificat public, la clé n'est pas affichée) :

        kubectl get secret staging-tls -n staging -o jsonpath='{.data.tls\.crt}' | base64 -d | openssl x509 -noout -enddate

- Renouveler (dossier temporaire hors du dépôt, jamais commité) :

        umask 077
        mkdir -p ~/secrets-tmp/tls && cd ~/secrets-tmp/tls
        tailscale cert --cert-file staging.crt --key-file staging.key amira.tail764250.ts.net
        cd ~/projetdevsecops/teamslot
        kubectl create secret tls staging-tls --cert=$HOME/secrets-tmp/tls/staging.crt --key=$HOME/secrets-tmp/tls/staging.key --namespace staging --dry-run=client -o yaml | kubeseal --cert ~/pub-cert.pem --format yaml > deploy/k8s/overlays/staging/sealed-secrets/staging-tls.yaml
        grep -c 'BEGIN' deploy/k8s/overlays/staging/sealed-secrets/staging-tls.yaml
        rm -rf ~/secrets-tmp/tls
        umask 022
        ls ~/secrets-tmp/tls

  Le dernier `ls` doit répondre `No such file or directory` : le dossier de la clé a bien disparu. Sinon, le supprimer à la main avant de continuer.

  Le `grep` doit afficher **0** (rien en clair). Puis PR et fusion : Argo CD déploie le nouveau secret.
- Le nom de la machine apparaît dans les journaux publics de transparence des certificats.

### Argo CD : sondes de repo-server

Le pod `argocd-repo-server` était tué par sa sonde liveness (le healthcheck répondait en environ 5 s pour une limite de 5 s, plus de 600 redémarrages). Les délais ont été augmentés à la main, hors Git :

        kubectl patch deployment argocd-repo-server -n argocd --type strategic -p '{"spec":{"template":{"spec":{"containers":[{"name":"argocd-repo-server","livenessProbe":{"timeoutSeconds":10,"failureThreshold":5},"readinessProbe":{"timeoutSeconds":10}}]}}}}'

Ce réglage est perdu si Argo CD est réinstallé ou mis à jour : le refaire.

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

## Walking skeleton (SCRUM-36)

Chaîne validée : modification d'une ligne → PR → fusion → CI → tag mis à jour dans `deploy/` → Argo CD synchronise → Grafana affiche la nouvelle version.

## Pare-feu des ports 6550 et 8080 (SCRUM-123)

Le cluster a été créé avec les ports 6550 (API Kubernetes) et 8080 (Ingress) sur `0.0.0.0` : ils étaient joignables depuis tout le réseau local du PC. Les lier à l'adresse Tailscale oblige à recréer le cluster, ce qui détruit les volumes des bases, Argo CD et la supervision. Pour un correctif d'un point, ce risque est inutile : on bloque donc ces ports par pare-feu, et la vraie correction est faite à la prochaine recréation du cluster.

Règle Windows (PowerShell administrateur), qui bloque 6550 et 8080 sur les cartes Wi-Fi et réseau local, tous profils. L'accès local (`localhost`, `kubectl`) et Tailscale ne sont pas concernés :

    New-NetFirewallRule -DisplayName "TeamSlot : bloquer 6550/8080 hors Tailscale (SCRUM-123)" -Direction Inbound -Protocol TCP -LocalPort 6550,8080 -InterfaceAlias "Wi-Fi","Connexion au réseau local" -Action Block -Profile Any

Si le PC a d'autres cartes réseau physiques, ajouter leur nom dans `-InterfaceAlias`. Pour retirer la règle :

    Remove-NetFirewallRule -DisplayName "TeamSlot : bloquer 6550/8080 hors Tailscale (SCRUM-123)"

Preuve : depuis un téléphone sur le même Wi-Fi, sans Tailscale, `http://<IP locale du PC>:8080` et `https://<IP locale du PC>:6550` ne répondent pas.

### Prochaine recréation du cluster

Lier les ports à l'adresse Tailscale (Git Bash, après `tailscale up`) :

    k3d cluster create teamslot --agents 1 -p "$(tailscale ip -4):8080:80@loadbalancer" -p "$(tailscale ip -4):8443:443@loadbalancer" --api-port "$(tailscale ip -4):6550"

Avant toute recréation : sauvegarder les clés de scellement (Sealed Secrets), hors du dépôt, et prévenir l'équipe. Si Tailscale n'est pas démarré, la liaison échoue.

### Accès Grafana et Argo CD

Les `port-forward` de Grafana (3001) et d'Argo CD (8444) utilisent `--address 0.0.0.0` : ils sont exposés au réseau local tant qu'ils tournent. Les arrêter après usage, ou utiliser `--address "$(tailscale ip -4)"`.
