# Use an official Maven image to build the application
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy the pom.xml and download dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy the source code and build the application
COPY src ./src
RUN mvn package -DskipTests

# Playwright Java image — ships with Java 17 + Chromium pre-installed
FROM mcr.microsoft.com/playwright/java:v1.51.0-jammy
WORKDIR /app

# Copy the JAR from the build stage
COPY --from=build /app/target/dockerbackend-0.0.1-SNAPSHOT.jar app.jar

# Expose the application port
EXPOSE 8080

# Run with --no-sandbox (required inside Docker/container environments)
ENTRYPOINT ["java", "-jar", "app.jar"]
