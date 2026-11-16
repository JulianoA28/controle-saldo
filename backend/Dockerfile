# Stage 1: Maven build (dependencies in a separate layer to leverage the cache)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# Stage 2: JRE and the jar only
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/balance-control-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
