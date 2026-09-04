# LLM Gateway — v3 Plan (the deliberate polish pass)

**Status: PARKED until we start.** Same discipline as always — this captures the whole scope before a line is written. The point of v3 is not "add everything"; it's to make the gateway *visibly complete, observable, and rigorous*, with every feature earning its place — and to make the **restraint** (what we deliberately left out) part of the story.

**Stack (unchanged +):** Java 25 · Spring Boot 4.1.1 · Maven · Postgres · Docker · **+ Micrometer / Prometheus** (metrics) · **+ k6** (load-test artifact, not a CI gate).

**Model for the build:** Opus 4.8. **Inherits `llm-gateway-preflight.md` in full** + the §A sanctions from prior runs.

---

## The hard scope line

**v3 is exactly these three things. Nothing else.**

1. **Multi-tenant control** — per-client rate limits and hard budget caps, enforced, managed via the admin API, surfaced on the dashboard.
2. **Observability** — Micrometer metrics, request-latency tracking, and latency percentiles (p50/p95/p99) on the dashboard.
3. **Ratcheted rigor** — raise the coverage/mutation thresholds a notch and add a runnable load-test artifact.

## Deliberately OUT of scope (this is a feature, not a gap — it goes in the README)

- **Redis / horizontal scale** — the gateway is single-instance; shared state would be solving a problem it doesn't have. Scoped out on purpose. *(Interview line: "single instance doesn't need it; here's how I'd add it if it did.")*
- **Streaming failover across providers** — narrow, high-effort, low-visibility. Kept as a documented "how I'd extend it," not built.
- **A full interactive admin write-UI** — multi-tenant management is done **API-first** (admin endpoints); the dashboard *displays* per-client config read-only. A create-client form is an optional stretch, not core.

A "Deliberately out of scope" section in the README turns this restraint into a visible signal of judgment. That section is a deliverable, not an afterthought.

---

## Phases

Same rules as every run: read first, TDD each substep, one commit per substep, full gate set green at each boundary, stop at §4 walls. Phases v3.1–v3.3 are **code-only, local, one continuous run** (nothing touches the VPS). The redeploy is separate and manual, like v2's.

### Phase v3.1 — Multi-tenant: per-client limits + budgets
Turns "a gateway" into "a multi-tenant gateway" — the single biggest leap in how complete it feels.
- **Per-client rate limit:** add a `rate_limit` to the client record (Flyway `V4`), defaulting to the global value when unset. The rate limiter already keeps a per-client bucket — this makes the *limit value* per-client too. Unset → falls back to the configured default (backward-compatible).
- **Per-client budget cap:** add a `budget` (hard spend cap) to the client record. When a client's accumulated cost crosses its cap, further requests are rejected with a clean **`402`** (`budget_exceeded`) in the error envelope until reset. This is enforceable and demonstrable *even in demo mode* (computed cost from the pricing table) — set a low cap, send traffic, watch it start rejecting. Great demo.
- **Admin API management:** admin-key-protected endpoints to create a client (returns the raw key **once**, stores only the bcrypt hash), set/update a client's rate limit and budget, and enable/disable a client. Every route admin-authenticated; a client key can never reach them.
- **Dashboard (read-only):** per-client rows show limit, budget, and spend-vs-cap. No new business logic in the UI — it reads the tested admin API.
- **Optional stretch (only if clean):** a minimal create-client form in the dashboard. Skip if it bloats the frontend.
- **TDD:** per-client limit enforced (distinct clients, distinct limits); budget cap rejects at the boundary and not before; cap reset re-enables; admin routes require admin auth; client key rejected from admin routes; unset limit/budget → safe defaults.
- **Exit:** full gate set green; v1/v2 behavior unchanged for clients with no limit/budget set.

### Phase v3.2 — Observability
Senior signal, demos beautifully, pairs perfectly with multi-tenancy (per-tenant metrics).
- **Micrometer** wired via Actuator: counters and timers for requests, cache hits/misses, provider calls, failovers, rate-limit and budget rejections — tagged per client where it makes sense.
- **Latency tracking:** time each request; expose **p50 / p95 / p99** through the **admin** `/api/stats` (protected — not a public metrics dump).
- **Prometheus endpoint:** wire `micrometer-registry-prometheus`, but keep the scrape endpoint **protected / not publicly exposed** on the demo box (a public metrics endpoint leaks internals — protecting it is the right call and a good judgment talking point). Document the decision.
- **Dashboard:** add a latency panel (p50/p95/p99) reading from the admin API.
- **TDD:** metrics increment on the right events; percentiles compute correctly; the metrics endpoint requires auth.
- **Exit:** full gate set green; latency percentiles visible on the dashboard; metrics endpoint protected.

### Phase v3.3 — Ratcheted rigor
Cheap, and it strengthens the "measured, not asserted" story you already lead with.
- **Raise the gates** a notch: e.g. JaCoCo 85→**88**% line, PIT 70→**80**% mutation — *only if the suite genuinely clears the higher bar*. If it can't clear it honestly, that's a §5 wall: do not lower it back, and do not fake coverage; either write the tests that earn the higher number or leave the threshold where it honestly sits and note it. Ratcheting is aspirational, not mandatory — an honest 72% beats a gamed 80%.
- **Load-test artifact:** a committed **k6** script that drives the gateway (mix of unique + repeated prompts to exercise cache + accounting), with a short results note in the README (throughput, latency under load). **Not a CI gate** (load tests are slow/flaky in CI) — a runnable, documented demonstration of load-testing awareness.
- **Exit:** gates green at whatever thresholds honestly hold; k6 script committed and documented.

---

## Deploy implications (new in v3)

- **Flyway `V4`** adds the per-client limit/budget columns — additive, applied on the next boot, exactly like v2's `V3`. The redeploy is the same short manual runbook as v2 (build → scp → restart → watch migration → verify), plus a dashboard rebuild.
- **No new services on the box.** No Redis, no separate metrics stack — Micrometer lives inside the app; the Prometheus endpoint (if wired) stays protected and unexposed. So the redeploy is as low-risk as v2's.
- **New admin write-endpoints** are on the public host — admin-key protected, same posture as the existing admin API. Threat-model note: creating clients returns a raw key once; never logged, never in git.

---

## What "done" looks like for v3

A gateway that: enforces per-client rate limits and hard budgets (demonstrable live), reports request/latency/cache/failover metrics with p50/p95/p99 on the dashboard, is managed via a clean admin API, holds ratcheted correctness gates, ships a load-test artifact — and has a README section that plainly states what was left out and why. Complete, observable, rigorous, and visibly the product of judgment rather than feature-piling.

## Sequencing

v3.1 → v3.2 → v3.3 as one continuous local code run (they're mostly independent; this order lets the dashboard work land together). Then one manual redeploy + dashboard rebuild. Then the README "deliberately out of scope" section, and update the CV/portfolio one-liners if the multi-tenant/observability additions are worth a mention (they are — "multi-tenant, observable" is a stronger descriptor).

## First move

One code brief covering v3.1–v3.3, `$0`, all local, inheriting the pre-flight — then the manual redeploy runbook after it's green and pushed.
