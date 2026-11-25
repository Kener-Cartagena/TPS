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
5. Merge once the checks are green. Required: *Build and test*, *Build
   Docker image* and *Migration guard* (**Backend CI**). Also review the
   **Security** workflow (*CodeQL* and *Dependency review*).
6. When a phase is complete, release it:
   1. On a `release/vX.Y.Z` branch from `develop`, set the version in
      `backend/pom.xml` to `X.Y.Z` (without `-SNAPSHOT`) and merge it into
      `develop` through a pull request.
   2. Open a pull request from `develop` into `main` and merge it.
   3. Tag the merge commit on `main` (`vX.Y.Z`).
   4. The next phase starts by bumping `develop` to the next
      `-SNAPSHOT` version (e.g. `0.5.0-SNAPSHOT`).

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
- REST endpoints follow [docs/api-conventions.md](docs/api-conventions.md):
  modules throw the exception categories of `shared.error` (not found, conflict,
  business rule violation, ...) with their own `ErrorCode` and never build error
  responses themselves — see
  [ADR 0004](docs/adr/0004-api-conventions-and-error-format.md).
- Every protected operation checks a permission from `identity.Permissions`
  with `@PreAuthorize`, never a role, and is added to `AuthorizationMatrixTests`
  — see [docs/security.md](docs/security.md).
- Code that needs the current time injects `java.time.Clock` instead of calling
  `Instant.now()`, so it can be tested with a fixed clock.
- Schema changes are new Flyway migrations; merged migrations are never edited
  — see [ADR 0002](docs/adr/0002-postgresql-and-flyway.md). The *Migration
  guard* check enforces it: it fails if a merged migration is modified, deleted
  or renamed, or if a new one is misnamed or older than the latest merged one.
  Check locally with
  `EVENT_NAME=pull_request BASE_REF=develop .github/scripts/check-migrations.sh`
  (after committing).
- Money is `shared.money.Money`, never `double`; amounts are moved only through
  ledger journal entries — see [docs/ledger.md](docs/ledger.md).
- Formatting follows [.editorconfig](.editorconfig) (4 spaces, LF line endings).
- Never commit secrets or `.env` files. Use `.env.example` for documented
  placeholders.
