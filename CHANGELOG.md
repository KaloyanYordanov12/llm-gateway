# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Pre-deploy — Demo-mode stub provider.** A `StubProviderClient` (name
  `stub`) that returns a canned response with a small plausible `Usage` and makes
  no network call, plus a `gateway.provider.mode` selector (`live` | `demo`,
  **default `live`**). In `demo` mode the registry default is the stub, so a
  public deployment cannot cost anything; requests still flow through the full
  `ProxyService` path (cache + usage), so the dashboard has real numbers.
  Fail-secure: `live` is the default and an unrecognized mode fails startup rather
  than silently selecting demo or silently spending.
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

- **Phase 2 — Rate limiter.** Per-client token bucket with an injected
  `Clock` (deterministic, zero-flaky tests), configurable capacity + refill
  (`gateway.rate-limit.*`, default 60 req/min). A filter running after auth on
  `/v1/*` returns `429` in the error envelope when a client is over its limit.

- **Phase 3 — Provider adapter + resilience.** `ProviderClient` abstraction with
  an `AnthropicProviderClient` (blocking `RestClient`), an N-provider
  `ProviderRegistry`, and Resilience4j retry + circuit breaker around provider
  calls (core modules wired manually — the Boot starter targets Boot 3). Added the
  `POST /v1/messages` proxy endpoint and a global exception handler mapping
  provider transport failures to `502` and an open circuit to `503`, both in the
  error envelope. All provider traffic is stubbed with WireMock ($0); the
  circuit-breaker state machine (closed → open → half-open → closed) is tested
  against simulated 5xx.

- **Phase 4 — Response cache.** In-memory Caffeine cache keyed by the SHA-256 of
  the canonical JSON over the locked field set, TTL configurable
  (`gateway.cache.*`, default 1h), with hit/miss counters for the dashboard. The
  proxy now serves cache hits without calling the provider. Non-zero-temperature
  requests that hit the cache return a valid prior completion by design (see
  Notes).

- **Phase 5 — Usage + cost accounting.** Config-driven pricing table
  (`gateway.pricing.*`) that doubles as the model allowlist — an unknown model is
  rejected with `400` (fail-secure). Token counts are parsed from the provider
  response, priced, and persisted per client (Flyway `V2__usage.sql`,
  Testcontainers). Usage is recorded once per billable (non-cached) call, so
  per-client totals sum exactly and never double-count.

- **Phase 6 — Management API.** Admin-key-guarded (`x-admin-key`, from
  `gateway.admin.key`; constant-time comparison), read-only endpoints:
  `GET /api/clients` (never exposes key hashes), `GET /api/usage?client=…`
  (per-client totals), and `GET /api/stats` (aggregate requests, spend, tokens,
  cache hit/miss, rate-limit rejections). A client `x-api-key` cannot reach
  `/api/*`.

- **Phase 7 — Dashboard.** A small React SPA (Vite) built by
  `frontend-maven-plugin` into Spring's static resources during `mvnw verify`, so
  one self-contained jar serves both the API and the UI. The "Gateway Console" is
  a read-only telemetry view over the Phase 6 API (system requests/spend/cache-hit
  rate/rate-limit rejections and a per-client table); it holds no business logic.
  The app is also forced to run in UTC (`-Duser.timezone=UTC`) so it connects to
  Postgres 17 regardless of host timezone.

### Notes

- **Cache hits on non-zero-temperature requests return a prior completion.** The
  cache key includes `temperature`, so an identical repeated request (even with
  `temperature > 0`) returns the previously sampled completion rather than
  resampling. This is intentional, documented behaviour, not a bug.

- **PIT mutation threshold now enforced from Phase 1 (≥70%), not Phase 2.** In
  Phase 0 the only logic was a health endpoint with no mutable branches, so a
  threshold was deferred. Phase 1 lands the first real branching logic (auth),
  which should not sit un-mutation-tested, so enforcement was brought forward one
  phase. Bringing a gate on *earlier* is a tightening (pre-flight §3, "ratchet up,
  never down"), not a weakening. From Phase 1 on, `mvn verify` runs the full gate
  set: tests + JaCoCo (85/80) + PIT (≥70%) + Checkstyle (0) + SpotBugs (0).
