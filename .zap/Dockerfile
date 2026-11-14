# SCRUM-129 : image du scan ZAP du staging, épinglée par version ET par empreinte (SCRUM-84).
# Ce fichier n'existe que pour que Dependabot (écosystème docker, .github/dependabot.yml) propose
# les nouvelles versions de ZAP : le workflow zap-staging.yml construit cette image et l'utilise telle quelle.
FROM ghcr.io/zaproxy/zaproxy:2.17.0@sha256:9624669db72f54d86c4b46a7064518bd9bf79d8c61a4c31befa9923489f328af
# Utilisateur non root de l'image officielle (Trivy DS002)
USER zap
