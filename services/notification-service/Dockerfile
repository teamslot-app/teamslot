# Contexte de build = racine du dépôt (ADR-015, règle 6) :
#   docker build -f services/notification-service/Dockerfile .
FROM maven:3.9-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320 AS build
WORKDIR /app
# Tous les pom.xml : le réacteur Maven lit tous les modules listés par le parent
# (sinon « Child module does not exist »). A compléter quand un module est ajouté au parent.
COPY pom.xml ./
COPY libs/events/pom.xml libs/events/
COPY services/service-template/pom.xml services/service-template/
COPY services/payment-service/pom.xml services/payment-service/
COPY services/match-service/pom.xml services/match-service/
COPY services/venue-service/pom.xml services/venue-service/
COPY services/identity-service/pom.xml services/identity-service/
COPY services/notification-service/pom.xml services/notification-service/
# Sources : seulement le service et les modules dont il dépend
COPY libs/events/src libs/events/src
COPY services/notification-service/src services/notification-service/src
# Cache BuildKit des dépendances Maven : réutilisé d'un build à l'autre, jamais copié dans l'image
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl services/notification-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine@sha256:5d7689f47d670aca766b9503a70bccc2d73b4eb9221ae8f714ae958313c4ce70
# CVE-2026-109908 (libfontenc, HIGH) : corrigé en 1.1.10-r0, absent de l image de base épinglée. Provisoire,
# comme venue-service (#248) : à retirer avec la prochaine empreinte de l image de base.
RUN apk upgrade --no-cache libfontenc
RUN addgroup -S -g 101 app && adduser -S -u 100 -G app app
WORKDIR /app
COPY --from=build /app/services/notification-service/target/*.jar app.jar
USER 100
EXPOSE 8086
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
