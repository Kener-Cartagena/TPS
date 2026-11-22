# ADR 0003 — Branching model and commit conventions

- **Status:** Accepted
- **Date:** 2026-11-21

## Context

Every change should be verified by CI before it is integrated, `main` should
always contain a stable, demonstrable version, and the history should be easy
to read. Later, continuous delivery will deploy automatically from `main`.

## Decision

### Branches

| Branch | Purpose |
|---|---|
| `main` | Stable releases only. Each completed phase is merged here and tagged (`v0.1.0`, `v0.2.0`, ...). Future deployments run from this branch. |
| `develop` | Integration branch. All work is merged here first through pull requests. |
| `feature/*` | New functionality, e.g. `feature/phase-1-api-foundation`. |
| `fix/*`, `test/*`, `docs/*`, `ci/*` | Fixes, tests, documentation, pipeline changes. |

Flow: `feature/*` → PR → `develop` → (phase complete) PR → `main` → tag.

`main` and `develop` are protected: changes arrive only through pull requests,
and the **Backend CI / Build and test** check must pass before merging.

### Commits

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/),
in English and in the imperative mood:

```
<type>(<optional scope>): <summary>
```

Types: `feat`, `fix`, `docs`, `test`, `refactor`, `build`, `ci`, `chore`.

Examples: `feat(ledger): add double-entry posting`,
`ci: add backend build workflow`, `docs: add ADR for branching model`.

### Versioning

Releases follow [Semantic Versioning](https://semver.org/). While the project is
pre-1.0, each completed phase increments the minor version.

## Consequences

- Positive: `main` is always releasable; every change has a reviewed PR with a
  CI result; history is self-describing.
- Negative: one extra pull request per phase (`develop` → `main`). Accepted,
  because it keeps unfinished work away from future automatic deployments.
