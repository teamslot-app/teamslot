#!/usr/bin/env bash
# SCRUM-105 : quelle image déployée en staging contient une bibliothèque, et en quelle version ?
# Lit les images déployées dans deploy/k8s/overlays/staging/kustomization.yaml, vérifie la SBOM
# signée attachée à chaque empreinte (cosign), puis y cherche le composant. Aucune image n'est
# reconstruite ni téléchargée : seule la SBOM (quelques centaines de Ko) est lue.
#
# Usage (depuis la racine du dépôt, Docker Desktop lancé) :
#   deploy/sbom/chercher-composant.sh lz4-java
set -euo pipefail

MOTIF="${1:?Usage : $0 <nom de bibliothèque, par exemple lz4-java>}"
KUSTOMIZATION="$(dirname "$0")/../k8s/overlays/staging/kustomization.yaml"
EMETTEUR="https://token.actions.githubusercontent.com"
IDENTITE="https://github.com/teamslot-app/teamslot/.github/workflows/java-service.yml@refs/heads/main"
# cosign dans un conteneur jetable : rien à installer. Remplaçable : COSIGN=cosign
COSIGN="${COSIGN:-docker run --rm ghcr.io/sigstore/cosign/cosign:v3.0.6}"

# Une ligne par image : "<nom>@<empreinte>", ou "<nom>" seul si l'image n'a pas d'empreinte
images=$(awk '
  /^images:/ { dedans = 1; next }
  dedans && /^[^ -]/ { dedans = 0 }
  dedans && /^- / { if (nom != "") print nom (emp != "" ? "@" emp : ""); nom = ""; emp = "" }
  dedans && /digest:/ { emp = $NF }
  dedans && /newName:/ { nom = $NF }
  END { if (nom != "") print nom (emp != "" ? "@" emp : "") }
' "$KUSTOMIZATION")

echo "Recherche de « $MOTIF » dans les images déployées en staging :"
for ref in $images; do
  case "$ref" in
    *@sha256:*) ;;
    *) echo "  $ref : pas d'empreinte (publiée avant SCRUM-104/105), pas de SBOM"; continue ;;
  esac
  if ! sortie=$($COSIGN verify-attestation --type cyclonedx \
        --certificate-oidc-issuer "$EMETTEUR" --certificate-identity "$IDENTITE" "$ref" 2>/dev/null); then
    echo "  $ref : pas de SBOM signée par la CI de main"
    continue
  fi
  # L'attestation est un JSON dont le champ "payload" (base64) contient la SBOM CycloneDX
  sbom=$(printf '%s\n' "$sortie" | head -n 1 | sed -n 's/.*"payload":"\([^"]*\)".*/\1/p' | base64 -d)
  trouves=$(printf '%s' "$sbom" | grep -o "pkg:[a-z]*/[^\"]*${MOTIF}@[^\"?]*" | sort -u || true)
  if [ -n "$trouves" ]; then
    echo "  ${ref%%@*} : CONTIENT"
    printf '%s\n' "$trouves" | sed 's/^/      /'
  else
    echo "  ${ref%%@*} : absent"
  fi
done
