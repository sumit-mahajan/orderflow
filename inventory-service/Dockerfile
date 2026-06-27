# Multi-stage build. Context is the repo root (see docker-compose build.context: .)
# so the parent POM + common module are available to the reactor build.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY common ./common
COPY inventory-service ./inventory-service
# Parent POM lists all modules; Maven fails if a declared module dir is missing.
COPY order-service ./order-service
COPY payment-service ./payment-service
COPY shipment-service ./shipment-service
RUN mvn -q -pl inventory-service -am -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/inventory-service/target/app-exec.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
