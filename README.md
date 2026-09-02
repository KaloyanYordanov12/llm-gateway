# LLM Gateway

An Anthropic-compatible LLM gateway — a single self-hosted service that proxies
`x-api-key` clients to LLM providers, adding authentication, per-client rate
limiting, response caching, and usage/cost accounting.

> **Status:** Phase 0 — scaffolding and correctness gates only. No provider
> integration yet.

## Quickstart

```bash
# Placeholder — full stack (app + Postgres) comes online in Phase 1.
docker compose up
```

The gateway then exposes its health endpoint:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

## Build & test

The Maven wrapper is committed, so no global Maven install is required:

```bash
./mvnw verify
```

A single `./mvnw verify` compiles, runs the tests, and enforces every
correctness gate below.

## Correctness / gates

`./mvnw verify` fails the build unless all of these pass:

| Gate | Tool | Threshold | Badge |
|---|---|---|---|
| Test coverage | JaCoCo | ≥ 85% line / 80% branch | _CI pending_ |
| Mutation score | PIT | ≥ 70% (enforced from **Phase 2**) | _CI pending_ |
| Static analysis | Checkstyle | 0 violations | _CI pending_ |
| Static analysis | SpotBugs | 0 findings (effort=max, threshold=low) | _CI pending_ |

Badges are filled in once CI publishes results.

> **PIT note:** the mutation threshold is intentionally not enforced in Phase 0
> (a health endpoint has no meaningful branching to mutate). It becomes a failing
> gate in Phase 2. See `CHANGELOG.md`.

## Development

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/).
A commit-message template is provided; enable it with:

```bash
git config commit.template .gitmessage
```

## License

[MIT](LICENSE)
