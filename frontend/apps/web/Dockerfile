# Contexte de build = frontend/ : le web importe le client généré de packages/shared.
#   docker build -f apps/web/Dockerfile frontend
FROM node:25-alpine@sha256:bdf2cca6fe3dabd014ea60163eca3f0f7015fbd5c7ee1b0e9ccb4ced6eb02ef4 AS build
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
# Modèle rempli au démarrage par 20-envsubst-on-templates.sh (API_UPSTREAM) : le même image marche en compose et en cluster
COPY apps/web/nginx.conf.template /etc/nginx/templates/default.conf.template
ENV API_UPSTREAM=http://gateway:8080
COPY --from=build /repo/frontend/apps/web/dist /usr/share/nginx/html
USER nginx
EXPOSE 8080
