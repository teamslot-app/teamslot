FROM maven:3.9-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine@sha256:51ab5e3302e7141ce665ca3ea85e8b5cd648eafbc3c0c90dd79d6537684e4555
# SCRUM-119 : correctifs Alpine absents de l'image de base (scan Trivy du 10/11 : brotli-libs CRITICAL,
# libexpat, musl, zlib HIGH). apk upgrade de tous les paquets plutôt qu'une liste : de nouveaux CVE corrigés
# apparaissent chaque semaine. A retirer avec la nouvelle empreinte de l'image de base (SCRUM-121).
RUN apk upgrade --no-cache
RUN addgroup -S -g 101 app && adduser -S -u 100 -G app app
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER 100
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
