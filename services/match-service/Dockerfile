# Contexte de build = racine du dépôt (ADR-015, règle 6) :
#   docker build -f services/match-service/Dockerfile .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Tous les pom.xml : le réacteur Maven lit tous les modules listés par le parent
# (sinon « Child module does not exist »). A compléter quand un module est ajouté au parent.
COPY pom.xml ./
COPY libs/events/pom.xml libs/events/
COPY services/service-template/pom.xml services/service-template/
COPY services/payment-service/pom.xml services/payment-service/
COPY services/match-service/pom.xml services/match-service/
COPY services/identity-service/pom.xml services/identity-service/
# Sources : seulement le service et les modules dont il dépend
COPY libs/events/src libs/events/src
COPY services/match-service/src services/match-service/src
# Cache BuildKit des dépendances Maven : réutilisé d'un build à l'autre, jamais copié dans l'image
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl services/match-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 101 app && adduser -S -u 100 -G app app
WORKDIR /app
COPY --from=build /app/services/match-service/target/*.jar app.jar
USER 100
EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
