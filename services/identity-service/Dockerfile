# Contexte de build = racine du dépôt (ADR-015, règle 6) :
#   docker build -f services/identity-service/Dockerfile .
FROM maven:3.9-eclipse-temurin-26@sha256:b2c1ad85954592f9928e84327c65201f308ad9b5d8ed7d5b823717c97bf23fbb AS build
WORKDIR /app
# Tous les pom.xml : le réacteur Maven lit tous les modules listés par le parent
# (sinon « Child module does not exist »). A compléter quand un module est ajouté au parent.
COPY pom.xml ./
COPY libs/events/pom.xml libs/events/
COPY services/service-template/pom.xml services/service-template/
COPY services/identity-service/pom.xml services/identity-service/
COPY services/payment-service/pom.xml services/payment-service/
COPY services/match-service/pom.xml services/match-service/
COPY services/venue-service/pom.xml services/venue-service/
# Sources : seulement le service (identity n'utilise pas libs/events)
COPY services/identity-service/src services/identity-service/src
# Cache BuildKit des dépendances Maven : réutilisé d'un build à l'autre, jamais copié dans l'image
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl services/identity-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine@sha256:51ab5e3302e7141ce665ca3ea85e8b5cd648eafbc3c0c90dd79d6537684e4555
RUN addgroup -S -g 101 app && adduser -S -u 100 -G app app
WORKDIR /app
COPY --from=build /app/services/identity-service/target/*.jar app.jar
USER 100
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
