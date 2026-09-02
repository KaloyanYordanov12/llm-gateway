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

### Notes

- **PIT mutation threshold deferred to Phase 2.** In Phase 0 the only logic is a
  health endpoint with essentially no mutable branches, so a mutation threshold
  would be vacuous. PIT enforcement (≥70%) switches on in Phase 2 (the rate
  limiter — the first phase with real branching logic). This is a documented,
  pre-authorized exception and the **only** gate deferral in the project; it is
  not a weakened gate.
