# Multi-stage build. Context is the repo root (see docker-compose build.context: .)
# so the parent POM + common module are available to the reactor build.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY common ./common
COPY inventory-service ./inventory-service
COPY order-service ./order-service
COPY payment-service ./payment-service
RUN mvn -q -pl payment-service -am -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/payment-service/target/app-exec.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
