# E-mail de vérification en staging (SCRUM-134)

notification-service envoie le code de vérification de l'inscription par SMTP.

| Où | Relais | Configuration |
|---|---|---|
| Local et CI | Mailpit (fausse boîte, rien ne part sur Internet) | `deploy/docker-compose.yml`, http://localhost:8025 |
| Staging | Brevo (`smtp-relay.brevo.com:587`, STARTTLS, plan gratuit 300 e-mails/jour) | secret scellé `notification-smtp` |

Sans le secret `notification-smtp`, l'envoi est **désactivé** : le service démarre normalement, l'événement est consommé et ignoré, le joueur pourra redemander un code.

## Ce qui est déjà en place

- `deploy/k8s/base/notification-service.yaml` : `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD` et `EMAIL_EXPEDITEUR` lus dans le secret `notification-smtp` (`optional: true`).
- `deploy/k8s/overlays/staging/network-policies.yaml` : sortie de notification-service vers le **port 587** sur Internet, **jamais** vers les réseaux privés (10/8, 172.16/12, 192.168/16, tailnet 100.64/10, 169.254/16).
- Authentification et STARTTLS **obligatoires** par défaut (`application.properties`) : un relais sans chiffrement est refusé.

## Créer le secret (une fois)

1. **Compte Brevo** de l'équipe, puis **SMTP & API → SMTP** : noter l'identifiant SMTP et générer une **clé SMTP** dédiée au staging, nommée `teamslot-staging` pour pouvoir la révoquer seule.
2. **Expéditeur vérifié** (Senders) : une adresse que l'équipe contrôle. `teamslot.app` ne nous appartient pas : l'utiliser serait de l'usurpation, et les e-mails seraient refusés ou classés en spam.
3. **Sceller sans rien afficher**, dans un dossier temporaire hors du dépôt. Les valeurs sont tapées au clavier (`read -s` n'affiche rien) et ne passent jamais par l'historique du shell :

        mkdir -p ~/secrets-tmp && cd ~/secrets-tmp && umask 077
        read -r  -p "Identifiant SMTP Brevo : " SMTP_USER
        read -rs -p "Clé SMTP Brevo : " SMTP_PASSWORD; echo
        read -r  -p "Expéditeur vérifié (TeamSlot <adresse>) : " EMAIL_EXPEDITEUR
        kubectl create secret generic notification-smtp --namespace staging \
          --from-literal=SMTP_HOST=smtp-relay.brevo.com --from-literal=SMTP_PORT=587 \
          --from-literal=SMTP_USER="$SMTP_USER" --from-literal=SMTP_PASSWORD="$SMTP_PASSWORD" \
          --from-literal=EMAIL_EXPEDITEUR="$EMAIL_EXPEDITEUR" --dry-run=client -o yaml \
          | kubeseal --cert ~/pub-cert.pem --format yaml > /d/teamslot/deploy/k8s/overlays/staging/sealed-secrets/notification-smtp.yaml
        unset SMTP_USER SMTP_PASSWORD EMAIL_EXPEDITEUR
        cd ~ && rm -rf ~/secrets-tmp && umask 022

   `--dry-run=client` : rien n'est envoyé au cluster, le Secret en clair n'existe que dans le tuyau vers `kubeseal`.
4. Vérifier qu'il n'y a rien en clair : `grep -cE "brevo|@|BEGIN" deploy/k8s/overlays/staging/sealed-secrets/notification-smtp.yaml` doit afficher **0**.
5. Ajouter `- sealed-secrets/notification-smtp.yaml` dans `deploy/k8s/overlays/staging/kustomization.yaml`, puis PR, relecture (Amira) et fusion : Argo CD crée le secret, et notification-service redémarre avec l'envoi activé.

## Vérifier en staging

1. S'inscrire sur le staging avec une boîte de test de l'équipe.
2. Le code arrive en quelques secondes (penser aux spams).
3. Dans les logs de notification-service : ni le code ni l'adresse.

## En cas de fuite de la clé SMTP

Révoquer la clé `teamslot-staging` dans Brevo **d'abord**, puis en générer une nouvelle et resceller le secret (étape 3). Supprimer le fichier de Git ne suffit pas : l'historique la garde.
