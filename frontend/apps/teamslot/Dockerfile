# Contexte de build = frontend/ : la plateforme joueur importe le client généré de packages/shared.
#   docker build -f apps/teamslot/Dockerfile frontend
FROM node:22-alpine@sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402 AS build
WORKDIR /repo/frontend/packages/shared
COPY packages/shared/package*.json ./
RUN npm ci
WORKDIR /repo/frontend/apps/teamslot
COPY apps/teamslot/package*.json ./
RUN npm ci
WORKDIR /repo/frontend
COPY packages/shared packages/shared
COPY apps/teamslot apps/teamslot
WORKDIR /repo/frontend/apps/teamslot
RUN npm run build

FROM nginxinc/nginx-unprivileged:alpine@sha256:378d0c32f6562f7fd9629170797f57d2b953aa09be983b3daaf4809bb039ca00
# SCRUM-119 : correctifs Alpine absents de l'image de base (scan Trivy du 10/11 : brotli-libs CRITICAL,
# curl, libexpat, libx11, musl, zlib HIGH). L'image tourne en nginx : root seulement pour apk, USER nginx plus bas.
# A retirer avec une nouvelle empreinte de nginx-unprivileged qui embarque ces versions.
USER root
RUN apk upgrade --no-cache
# Modèle rempli au démarrage par 20-envsubst-on-templates.sh (API_UPSTREAM) : le même image marche en compose et en cluster
COPY apps/teamslot/nginx.conf.template /etc/nginx/templates/default.conf.template
ENV API_UPSTREAM=http://gateway:8080
COPY --from=build /repo/frontend/apps/teamslot/dist /usr/share/nginx/html
USER nginx
EXPOSE 8080
