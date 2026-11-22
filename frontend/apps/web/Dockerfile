# Contexte de build = frontend/ : le web importe le client généré de packages/shared.
#   docker build -f apps/web/Dockerfile frontend
FROM node:26-alpine@sha256:8996bb5821d62a8fd9bb27c1cb07462f3fdce6e47d640c6a881ead0b064ce216 AS build
WORKDIR /repo/frontend/packages/shared
COPY packages/shared/package*.json ./
RUN npm ci
WORKDIR /repo/frontend/apps/web
COPY apps/web/package*.json ./
RUN npm ci
WORKDIR /repo/frontend
COPY packages/shared packages/shared
COPY apps/web apps/web
WORKDIR /repo/frontend/apps/web
RUN npm run build

FROM nginxinc/nginx-unprivileged:alpine@sha256:378d0c32f6562f7fd9629170797f57d2b953aa09be983b3daaf4809bb039ca00
# SCRUM-119 : correctifs Alpine absents de l'image de base (scan Trivy du 10/11 : brotli-libs CRITICAL,
# curl, libexpat, libx11, musl, zlib HIGH). L'image tourne en nginx : root seulement pour apk, USER nginx plus bas.
# A retirer avec une nouvelle empreinte de nginx-unprivileged qui embarque ces versions.
USER root
RUN apk upgrade --no-cache
# Modèle rempli au démarrage par 20-envsubst-on-templates.sh (API_UPSTREAM) : le même image marche en compose et en cluster
COPY apps/web/nginx.conf.template /etc/nginx/templates/default.conf.template
ENV API_UPSTREAM=http://gateway:8080
COPY --from=build /repo/frontend/apps/web/dist /usr/share/nginx/html
USER nginx
EXPOSE 8080
