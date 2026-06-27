# Multi-stage build. Context is the repo root (see docker-compose build.context: .)
# so the parent POM + common module are available to the reactor build.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY common ./common
COPY order-service ./order-service
# Parent POM lists all modules; Maven fails if a declared module dir is missing.
COPY inventory-service ./inventory-service
COPY payment-service ./payment-service
COPY shipment-service ./shipment-service
RUN mvn -q -pl order-service -am -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/order-service/target/app-exec.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
