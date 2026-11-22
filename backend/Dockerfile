# syntax=docker/dockerfile:1

# ---- Build stage: compile and package the application with the Maven Wrapper ----
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

# Copy build descriptors first so dependency downloads are cached as a layer
# and only re-run when pom.xml changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src src
# Tests run in CI; the image build only packages the application.
RUN ./mvnw -B -ntp package -DskipTests \
    && cp target/tps-*.jar app.jar

# ---- Runtime stage: minimal JRE image, no build tools or sources ----
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Run as an unprivileged user, never as root.
RUN addgroup -S tps && adduser -S tps -G tps
USER tps

COPY --from=build /workspace/app.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
