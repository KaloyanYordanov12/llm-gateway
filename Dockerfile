# syntax=docker/dockerfile:1

# ---- Build stage: compile, test, and package the app on a Temurin 25 JDK ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy the Maven wrapper and pom first so dependency resolution is cached
# independently of source changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

# Now copy sources (Java + the React frontend) and build the jar. Tests are
# skipped HERE deliberately: from Phase 1 on, the suite uses Testcontainers, which
# needs a Docker daemon not available inside an image build. The full gate set
# (tests + JaCoCo + PIT + Checkstyle + SpotBugs) runs via `./mvnw verify` locally
# and in CI, which is the source of truth; this stage only produces the jar. The
# frontend-maven-plugin still runs, so the SPA is bundled into the jar.
COPY src/ src/
COPY frontend/ frontend/
RUN ./mvnw -B -ntp package -DskipTests

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
# Run in UTC: Postgres 17 rejects deprecated JVM tz aliases (e.g. "Europe/Kiev")
# during the JDBC handshake. UTC is also the right default for a server.
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-jar", "/app/app.jar"]
