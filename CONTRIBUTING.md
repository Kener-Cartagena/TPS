# Contributing

This document describes the day-to-day workflow for the TPS repository. The
reasoning behind it is recorded in
[ADR 0003](docs/adr/0003-branching-and-commit-conventions.md).

## Workflow

1. Start from an up-to-date `develop`:
   ```bash
   git switch develop
   git pull
   git switch -c feature/<short-description>
   ```
2. Make small, focused commits using [Conventional Commits](https://www.conventionalcommits.org/).
3. Run the build locally before pushing:
   ```bash
   cd backend && ./mvnw verify
   ```
4. Push the branch and open a pull request into `develop`. Fill in the PR
   template.
5. Merge once **Backend CI / Build and test** is green.
6. When a phase is complete, open a pull request from `develop` into `main`,
   merge it, and tag the release (`vX.Y.Z`).

## Branch names

`feature/*`, `fix/*`, `test/*`, `docs/*`, `ci/*` — lowercase, words separated
by hyphens, e.g. `feature/phase-3-accounts-ledger`.

## Commit messages

```
<type>(<optional scope>): <imperative summary, max ~72 chars>
```

| Type | Use for |
|---|---|
| `feat` | New functionality |
| `fix` | Bug fixes |
| `test` | Adding or changing tests only |
| `docs` | Documentation only |
| `refactor` | Code changes that neither fix a bug nor add a feature |
| `build` | Build system or dependencies (Maven, Docker) |
| `ci` | GitHub Actions workflows |
| `chore` | Maintenance that fits nowhere else |

## Code conventions

- Code is organized by business module — see
  [ADR 0001](docs/adr/0001-modular-monolith.md).
- Schema changes are new Flyway migrations; merged migrations are never edited
  — see [ADR 0002](docs/adr/0002-postgresql-and-flyway.md).
- Formatting follows [.editorconfig](.editorconfig) (4 spaces, LF line endings).
- Never commit secrets or `.env` files. Use `.env.example` for documented
  placeholders.
