# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Phase 0 — Scaffolding & gates.** Empty-but-real Spring Boot 4.1.1 project on
  Java 25 (Temurin), built with Maven via the committed wrapper (`./mvnw`).
- Feature-sliced package layout under `dev.kaloyanyordanov.llmgateway`
  (`proxy`, `auth`, `ratelimit`, `provider`, `cache`, `usage`, `management`,
  `config`, `error`).
- `/actuator/health` endpoint with an end-to-end smoke test proving the web layer
  and test harness run.
- Correctness gates wired into `./mvnw verify` as **failing** gates: JaCoCo
  coverage (85% line / 80% branch), Checkstyle (0 violations), SpotBugs
  (0 findings).
- PIT mutation-testing plumbing (report generated on demand).
- Multi-stage Dockerfile (Temurin 25 JDK build stage, `eclipse-temurin:25-jre`
  runtime, non-root) and `docker-compose.yml` (app + Postgres, wired for Phase 1).
- GitHub Actions CI running `./mvnw -B verify` on Temurin 25.

- **Phase 1 — Client auth + request contract.**
  - `x-api-key` authentication: missing/invalid key → `401` in the locked error
    envelope; valid key proceeds. Client API keys stored bcrypt-hashed.
  - Anthropic-shaped request/response DTOs (locked field set for the cache key).
  - `Client` JPA entity + Flyway `V1__clients.sql`; Postgres via Testcontainers.
  - Error envelope model (`{type, error:{type,message}, request_id}`).
  - Housekeeping: Mockito attached as an explicit JVM agent (JDK 25 self-attach
    deprecation); CI actions bumped to `checkout@v5` / `setup-java@v5`.

### Notes

- **PIT mutation threshold now enforced from Phase 1 (≥70%), not Phase 2.** In
  Phase 0 the only logic was a health endpoint with no mutable branches, so a
  threshold was deferred. Phase 1 lands the first real branching logic (auth),
  which should not sit un-mutation-tested, so enforcement was brought forward one
  phase. Bringing a gate on *earlier* is a tightening (pre-flight §3, "ratchet up,
  never down"), not a weakening. From Phase 1 on, `mvn verify` runs the full gate
  set: tests + JaCoCo (85/80) + PIT (≥70%) + Checkstyle (0) + SpotBugs (0).
