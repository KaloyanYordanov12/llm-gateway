# LLM Gateway

[![CI](https://github.com/KaloyanYordanov12/llm-gateway/actions/workflows/ci.yml/badge.svg)](https://github.com/KaloyanYordanov12/llm-gateway/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

An Anthropic-compatible LLM gateway: a single self-hosted service that sits in
front of an LLM provider and adds the operational layer you actually need in
production. Clients call it with an `x-api-key`; it authenticates them,
rate-limits per client, serves responses from a cache, proxies to the provider
through retry + a circuit breaker, and records token usage and cost. A read-only
admin API and a bundled dashboard expose the numbers.

Built with Java 25 and Spring Boot 4.1.1, correctness enforced by an
un-cheatable `mvnw verify` (coverage, mutation, static analysis), containerized,
and deployed live.

## Live demo

**<https://gateway.kaloyanyordanov.dev/>** — open the dashboard and enter the
admin key to view live telemetry; health is at
[`/actuator/health`](https://gateway.kaloyanyordanov.dev/actuator/health).

> **The public deployment runs in demo mode and costs nothing, by design.** It is
> configured with `GATEWAY_PROVIDER_MODE=demo`, which selects a **stub provider**
> that returns a canned response and makes **no real model call** — so no amount
> of traffic to the public box can spend a cent. Everything else is real: the
> request still flows through auth, rate limiting, the cache, and usage/cost
> accounting, so the dashboard shows genuine numbers. Switching to the real
> Anthropic provider is a single flag: `GATEWAY_PROVIDER_MODE=live`. `live` is the
> fail-secure default in code; `demo` is a loud, explicit opt-in for the public
> box.

## Architecture

```mermaid
flowchart LR
    client["Client<br/>(x-api-key)"] --> auth[Auth]
    auth --> rl[Rate limit]
    rl --> cache{Cache}
    cache -- hit --> resp[Response]
    cache -- miss --> prov["Provider<br/>retry + circuit breaker"]
    prov --> usage[Usage + cost accounting]
    usage --> resp
    prov -. demo mode .-> stub[Stub provider]

    usage --> db[(Postgres)]
    db --> api["Admin API /api/*<br/>(x-admin-key)"]
    api --> dash[Dashboard]
```

Requests enter through servlet filters (auth → rate limit), reach the proxy,
which checks the cache and, on a miss, calls the provider behind Resilience4j
retry + circuit breaker. Billable calls are priced and persisted to Postgres. The
admin API reads those records; the React dashboard is a read-only view over it.

## Why it's correct

The point of this project isn't that it has features — it's that the behavior is
*measured*, not asserted. Each invariant below is proven by tests that run in the
gated build:

- **Auth is fail-closed.** A missing or invalid `x-api-key` gets `401` in the
  Anthropic-shaped error envelope; only bcrypt-matched enabled clients proceed.
- **Rate limiting is deterministic.** A per-client token bucket driven by an
  **injected `Clock`** — capacity/refill invariants (and `429` over-limit) are
  proven with a fake clock, so the tests are zero-flaky.
- **The cache key is deterministic and complete.** SHA-256 over the canonical
  JSON of the locked request field set: identical requests hit, a one-token
  difference misses, entries expire on TTL (proven with a fake ticker).
- **Cost accounting never double-counts.** Usage is recorded once per *billable*
  (non-cached) call, so a cache hit is free and per-client totals sum exactly.
- **The circuit breaker's state machine is exhaustively tested.**
  closed → open → half-open → closed, driven against a WireMock provider
  simulating 5xx failures.
- **Model handling is fail-secure.** The pricing table doubles as the model
  allowlist; an unknown model is rejected with `400` rather than proxied unpriced.

Provider HTTP is stubbed with WireMock and Postgres runs under Testcontainers, so
these are real integration tests — nothing that should be integration-tested is
mocked away.

## Correctness / gates

A single `./mvnw verify` compiles, runs every test, and fails the build unless all
of these hold. The same command runs in CI on every push and pull request.

| Gate | Tool | Threshold | Enforcement |
|---|---|---|---|
| Test coverage | JaCoCo | ≥ 85% line / 80% branch | fails `mvnw verify` |
| Mutation score | PIT | ≥ 70% | fails `mvnw verify` |
| Static analysis | Checkstyle | 0 violations | fails `mvnw verify` |
| Static analysis | SpotBugs | 0 findings (effort=max, threshold=low) | fails `mvnw verify` |

113 tests, all green. Thresholds are read straight from `pom.xml`; they ratchet
up, never down.

> **PIT note:** mutation testing is *enforced from Phase 1* (the first phase with
> real branching logic). It was deferred only in Phase 0, where a health endpoint
> has nothing meaningful to mutate. See `CHANGELOG.md`.

## Run it

**Local stack (app + Postgres) with Docker Compose:**

```bash
docker compose up
curl http://localhost:8080/actuator/health   # {"status":"UP"}
```

The dashboard is then served at <http://localhost:8080/>.

**Pull the published image** (built and pushed to GHCR by CI on `main`):

```bash
docker pull ghcr.io/kaloyanyordanov12/llm-gateway:latest
```

**Full gated build** (the Maven wrapper is committed — no global Maven needed):

```bash
./mvnw verify
```

## Configuration

All configuration is via environment variables; **no secrets belong in this repo
or image** — the values below are placeholders.

| Variable | Purpose | Default |
|---|---|---|
| `GATEWAY_PROVIDER_MODE` | `live` proxies to the real provider; `demo` uses the no-network stub | `live` |
| `GATEWAY_ADMIN_KEY` | Admin key required on every `/api/*` route (`x-admin-key`) | `dev-admin-key` (dev only — override) |
| `DB_URL` | Postgres JDBC URL | `jdbc:postgresql://localhost:5432/gateway` |
| `DB_USER` / `DB_PASSWORD` | Postgres credentials | `gateway` / `gateway` |

`demo` vs `live` is the safety switch: `demo` cannot make a real provider call, so
a public box is spend-proof; `live` is the fail-secure default and an unrecognized
value fails startup rather than guessing.

## API surface

- `POST /v1/messages` — Anthropic-compatible proxy; authenticated with `x-api-key`.
- `GET /api/clients` · `GET /api/usage?client=<id>` · `GET /api/stats` — read-only
  admin API, authenticated with `x-admin-key`. Never exposes key hashes.
- `GET /actuator/health` — liveness/readiness.

## Stack

Java 25 (Temurin) · Spring Boot 4.1.1 · Maven · PostgreSQL + Flyway migrations ·
Resilience4j (retry + circuit breaker) · Caffeine cache · React/Vite dashboard
built into the jar. Production runs the jar under systemd; a multi-stage Docker
image is also built and published to GHCR as a reproducible artifact.

## Development

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/).
A commit-message template is provided; enable it with:

```bash
git config commit.template .gitmessage
```

## License

[MIT](LICENSE)
