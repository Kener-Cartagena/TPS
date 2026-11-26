# Transaction Processing System (TPS)

[![Backend CI](https://github.com/Kener-Cartagena/TPS/actions/workflows/backend-ci.yml/badge.svg?branch=develop)](https://github.com/Kener-Cartagena/TPS/actions/workflows/backend-ci.yml)
[![Security](https://github.com/Kener-Cartagena/TPS/actions/workflows/security.yml/badge.svg?branch=develop)](https://github.com/Kener-Cartagena/TPS/actions/workflows/security.yml)

A backend platform for processing financial transactions — accounts, a
double-entry ledger, internal transfers, payments through simulated providers,
reversals, and reconciliation — built with **Java 25** and **Spring Boot 4**.

> **Status: Phase 4 — transaction processing engine.** Deposits and withdrawals
> move money through a transaction engine with a controlled lifecycle, idempotent
> retries (`Idempotency-Key`: a retry never moves money twice), account status and
> funds rules, and a fixed lock order that stays correct under concurrency. Every
> movement is a balanced, immutable entry in a double-entry ledger, enforced by the
> application and by PostgreSQL itself. Transfers and reversals come in Phase 5.
> See the [roadmap](#roadmap).
>
> TPS is a portfolio project. It uses simulated financial providers and is not
> certified or intended to process real customer funds.

## Tech stack

| Area | Technology |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4 (Spring Web MVC, Spring Data JPA, Bean Validation, Actuator) |
| Security | Spring Security 7: stateless JWT (OAuth 2.0 resource server), method security, bcrypt |
| Architecture | Spring Modulith (module boundaries verified in tests) |
| API docs | OpenAPI 3 with springdoc-openapi (Swagger UI) |
| Database | PostgreSQL 18, Flyway migrations |
| Testing | JUnit 5, Spring Boot Test, Spring Security Test, Testcontainers, JaCoCo (coverage) |
| Build | Maven (wrapper included) |
| Infrastructure | Docker, Docker Compose, GitHub Actions, CodeQL, Dependabot |

## Architecture

TPS is a **modular monolith**: a single Spring Boot application divided into
business modules with enforced boundaries. Each module is a direct sub-package
of `io.github.kenercartagena.tps`; [Spring Modulith](https://spring.io/projects/spring-modulith)
fails the build if a module uses another module's internals.

| Module | Responsibility |
|---|---|
| `shared` | Cross-cutting building blocks: error categories and codes, API base paths, global error handling, request id logging, clock, OpenAPI metadata |
| `identity` | Users, login (JWT), roles and permissions, account lockout — see [docs/security.md](docs/security.md) |
| `ledger` | General ledger: double-entry journal entries, balances, running balances, verification — see [docs/ledger.md](docs/ledger.md) |
| `accounts` | Account holders (DNI, RTN, passport), customer accounts with check-digit numbers, status and its history |

Dependencies only go one way: `accounts → ledger → shared`, and every business
module uses `identity` only for its permission names. Business modules
(transactions, transfers, payments, ...) are added phase by phase.
Architectural decisions are documented as ADRs in [docs/adr](docs/adr/README.md),
and the REST rules in [docs/api-conventions.md](docs/api-conventions.md).

## Repository layout

```
.
├── backend/              Spring Boot application (Maven project)
│   ├── src/main/java/    Application code, organized by business module
│   ├── src/main/resources/
│   │   ├── application*.yml   Configuration per profile (dev, prod)
│   │   └── db/migration/      Flyway SQL migrations
│   └── Dockerfile        Multi-stage production image
├── docs/
│   ├── PLAN.md           Development plan and phases
│   ├── api-conventions.md  REST API and error format rules
│   ├── security.md       Security model: roles, permissions, authentication
│   ├── ledger.md         Accounts and ledger: double entry, rules, endpoints
│   ├── transactions.md   Transaction engine: lifecycle, idempotency, concurrency
│   ├── api/openapi.json  Reviewed snapshot of the API contract (checked in CI)
│   └── adr/              Architecture Decision Records
├── .github/
│   ├── workflows/        CI pipelines (GitHub Actions)
│   ├── scripts/          Scripts used by the pipelines (migration and API guards, summary)
│   └── dependabot.yml    Automated dependency updates
└── docker-compose.yml    Local environment (PostgreSQL + optional backend)
```

## Getting started

### Prerequisites

- Java 25 (JDK)
- Docker (Docker Desktop on Windows/macOS)

Maven does not need to be installed — use the included wrapper (`./mvnw`,
or `mvnw.cmd` on Windows CMD/PowerShell).

### Run locally

```bash
# 1. Optional: customize local settings
cp .env.example .env

# 2. Start PostgreSQL
docker compose up -d

# 3. Run the application (dev profile is the default)
cd backend
./mvnw spring-boot:run
```

On startup the application connects to PostgreSQL, applies all Flyway
migrations and, on an empty database, creates the local administrator
**`admin` / `admin-local-password`** (throwaway local credentials). Then open:

| URL | What |
|---|---|
| <http://localhost:8080/swagger-ui.html> | Interactive API documentation (Swagger UI) |
| <http://localhost:8080/v3/api-docs> | OpenAPI 3 document (JSON) |
| <http://localhost:8080/actuator/health> | Health (`/liveness` and `/readiness` probes below it) |
| <http://localhost:8080/actuator/info> | Application name, version and Java runtime |

Every API call except login needs a token:

```bash
# Log in and copy "accessToken" from the response
curl -X POST http://localhost:8080/api/v1/auth/tokens   -H "Content-Type: application/json"   -d '{"username": "admin", "password": "admin-local-password"}'

# Call the API with it
curl http://localhost:8080/api/v1/users/me -H "Authorization: Bearer <accessToken>"
```

In Swagger UI, log in with `POST /api/v1/auth/tokens`, click **Authorize** and
paste the token. Roles, permissions and all endpoints:
[docs/security.md](docs/security.md).

The administrator only manages users (segregation of duties). To work with
accounts, create an `OPERATOR` (and an `AUDITOR` to read the ledger) with
`POST /api/v1/users`, log in as that user, then follow the examples in
[docs/ledger.md](docs/ledger.md#example-open-an-account-as-an-operator); to move
money, [docs/transactions.md](docs/transactions.md#example-a-deposit-and-its-retry-as-an-operator).

### Run everything in containers

```bash
docker compose --profile app up --build
```

This builds the backend image and runs it with the `prod` profile against the
PostgreSQL container, on <http://localhost:8080>. The backend container reports
`healthy` once its readiness probe (application + database) passes:
`docker compose ps`.

### Run the tests

```bash
cd backend
./mvnw verify
```

Tests start a real PostgreSQL instance with Testcontainers, so Docker must be
running. No local database is required.

## Configuration

| Profile | When | Database connection | Logs |
|---|---|---|---|
| `dev` (default) | Local development | Local Docker PostgreSQL (defaults in `application-dev.yml`) | Plain text, with request id |
| `prod` | Docker image, deployments | Required environment variables, no defaults | JSON (Elastic Common Schema) |

Environment variables for deployments (`prod` profile):

| Variable | Required | Purpose |
|---|---|---|
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Yes | Database connection (Spring Boot standard) |
| `TPS_SECURITY_JWT_SECRET` | Yes | Token signing key, Base64, at least 32 bytes (`openssl rand -base64 32`) |
| `TPS_SECURITY_BOOTSTRAP_ADMIN_USERNAME`, `TPS_SECURITY_BOOTSTRAP_ADMIN_PASSWORD` | First start only | Creates the first administrator when the database has no users |

The application refuses to start if a required value is missing. All security
settings: [docs/security.md](docs/security.md#configuration).

## API conventions

All endpoints live under `/api/v1`. Successful responses return the resource
itself; errors use [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457)
with a stable `code` and the request id:

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "The requested endpoint does not exist.",
  "instance": "/api/v1/unknown",
  "code": "NOT_FOUND",
  "requestId": "3f2a9c1e-6b1d-4a57-9f1e-2a4c8d0b7e11",
  "timestamp": "2026-11-23T18:04:12.345Z"
}
```

Every response carries an `X-Request-Id` header that also appears in the logs.
Requests are authenticated with `Authorization: Bearer <token>`; lists are
paginated (`?page=0&size=20`). Full rules:
[docs/api-conventions.md](docs/api-conventions.md).

## Continuous integration

Every push and pull request to `main` or `develop` runs
[Backend CI](.github/workflows/backend-ci.yml), with three parallel jobs:

| Job | What it checks |
|---|---|
| **Build and test** | Compiles with Java 25, runs the full test suite (including database, concurrency and module boundary tests against PostgreSQL), publishes a *Test results* check, writes a tests and coverage summary on the run page, and uploads test and coverage (JaCoCo) reports as artifacts |
| **Build Docker image** | Builds the production image from `backend/Dockerfile` (not published) |
| **Migration guard** | Fails if a merged Flyway migration is modified, deleted or renamed, or if a new one is misnamed or out of order |

[Security](.github/workflows/security.yml) runs on every push and pull request
to `main` or `develop`, and every Monday:

| Job | What it checks |
|---|---|
| **CodeQL** (`java-kotlin`, `actions`) | Static security analysis of the Java code and of the workflow files; findings appear in the *Security* tab |
| **Dependency review** | Pull requests only: fails if the PR adds a dependency with a known vulnerability (moderate or worse) |

Pull requests cannot be merged unless CI passes.
[Dependabot](.github/dependabot.yml) opens weekly pull requests to update Maven
dependencies, GitHub Actions and Docker base images.

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| 0 | Project foundation and architecture | ✅ Done |
| 1 | Core backend and API foundation | ✅ Done |
| 2 | Identity, security and access control | ✅ Done |
| 3 | Accounts and financial ledger | ✅ Done |
| 4 | Transaction processing engine | ✅ Done |
| 5 | Internal transfers and reversals | Planned |
| 6 | Payment processing and simulated integrations | Planned |
| 7 | Reconciliation and operational reliability | Planned |
| 8 | Advanced testing and security hardening | Planned |
| 9 | Deployment and portfolio presentation | Planned |
| 10 | React + TypeScript dashboard | Planned |

Details for every phase: [docs/PLAN.md](docs/PLAN.md).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the branching model and commit
conventions.
