# syntax=docker/dockerfile:1

# ---- Build stage: compile, test, and package the app on a Temurin 25 JDK ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy the Maven wrapper and pom first so dependency resolution is cached
# independently of source changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

# Now copy sources and build. `package` runs the unit/integration tests in the
# build stage (per the runtime-image decision: tests run here, not at runtime).
COPY src/ src/
RUN ./mvnw -B -ntp package

# ---- Runtime stage: slim JRE, non-root ----
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Run as a non-root system user.
RUN groupadd --system app && useradd --system --gid app --home-dir /app app

# Copy only the repackaged Spring Boot fat jar from the build stage.
COPY --from=build /workspace/target/*.jar app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
