# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **v3.1 — Multi-tenant: per-client rate limits + budgets.** Nullable `rate_limit`
  and `budget` columns on `clients` (Flyway `V4`, additive/forward-only): a client
  with neither set behaves exactly as before (global default limit, no cap). The
  per-client token bucket now takes its capacity from the client's own
  `rate_limit` when set (rebuilding the bucket if the limit changes), so distinct
  clients enforce distinct limits independently. A hard `budget` cap is enforced
  from accumulated usage cost before any cache lookup or provider call: once spend
  reaches the cap, requests are rejected with **`402`** (`budget_exceeded`) in the
  locked envelope until the cap is raised — proven from the pricing table's
  computed cost (no real spend). Admin-key-protected write endpoints manage
  tenancy: `POST /api/clients` generates an `sk-gw-` key, stores only its bcrypt
  hash, and reveals the raw key **once**; `PATCH /api/clients/{id}` updates
  `rate_limit`/`budget`/`enabled`. A client `x-api-key` can never reach these
  routes. The read-only dashboard gains per-client rate-limit, budget, and
  spend-vs-cap columns (a create-client form was deliberately skipped to keep the
  SPA read-only — management is API-first).
- **v2.3 — Streaming (SSE).** Opt-in via `stream:true` (excluded from the cache
  key, so a streamed and non-streamed request for the same messages share a cache
  entry). Blocking + virtual threads, not reactive: the JDK `HttpClient` reads the
  upstream SSE line-by-line, forwarding each delta downstream via a Spring
  `SseEmitter` while accumulating text + tokens. On clean completion usage is
  recorded and the assembled result cached (so a later identical request — even
  non-streamed — is a hit, replayed from cache); a mid-stream abort records a
  **partial** usage row flagged incomplete and caches nothing, so spend is never
  lost or double-counted. The non-streaming path is byte-for-byte unchanged. Usage
  gains a `complete` flag (Flyway `V3`). All provider traffic (including streamed
  responses) is stubbed with WireMock ($0).
- **v2.2 — Per-model routing rules.** A pure, deterministic `ModelRouter` resolves
  a logical model to an ordered list of concrete (provider, upstream-model)
  targets from configuration (`gateway.routing.rules`) — logical→concrete mapping,
  ordered fallback, and optional cost-preference sorting; adding a rule is config,
  not code. A `ProviderRouter` executes the route (rewriting only the upstream
  model, failing over across targets on availability errors) in place of the fixed
  default. v1 is preserved: with no rules a request goes to the default provider
  unchanged, an unknown/unpriced model still fails `400` (pricing governs
  billability), and billing stays on the client's logical model. Unknown providers
  in routing rules fail startup.
- **v2.1 — Second provider + live failover.** An `OpenAiProviderClient` adapter
  (translating the gateway's normalized shape to/from OpenAI chat-completions,
  resilience-wrapped like the Anthropic client) and a `FailoverProviderClient`
  that fails over across a config-driven ordered chain (`gateway.provider.chain`).
  When the primary's circuit is open or it fails after retries (5xx/transport),
  traffic transparently moves to the secondary; a 4xx propagates unchanged; both
  down → a clean `503`. An empty chain preserves v1 single-provider behavior, and
  an unknown provider name in the chain fails startup (fail-secure). Proven
  end-to-end against two WireMock providers.
- **Pre-deploy — CI Docker build + GHCR publish.** A separate `docker` CI job
  (`needs: build`) builds the image on GitHub runners so a Docker-only breakage
  can't slip past — build-only on pull requests, and on pushes to `main` it also
  publishes `ghcr.io/kaloyanyordanov12/llm-gateway` (tags `latest` + short SHA)
  using the built-in `GITHUB_TOKEN` (`packages: write`). The image is a
  portfolio/repro artifact; the production runtime stays jar-under-systemd.
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

### Changed

- **Docs — README rewritten** for the shipped, deployed state: live demo link,
  honest demo-mode/$0 framing, architecture, correctness invariants, real gate
  thresholds, CI/GHCR, and configuration (replacing the frozen Phase 0 content).
- **Docs — README documents v2** (failover, per-model routing, SSE streaming),
  including the noted streaming-failover scope boundary.

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
