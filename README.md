# Transaction Processing System (TPS)

[![Backend CI](https://github.com/Kener-Cartagena/TPS/actions/workflows/backend-ci.yml/badge.svg?branch=develop)](https://github.com/Kener-Cartagena/TPS/actions/workflows/backend-ci.yml)

A backend platform for processing financial transactions — accounts, a
double-entry ledger, internal transfers, payments through simulated providers,
reversals, and reconciliation — built with **Java 25** and **Spring Boot 4**.

> **Status: Phase 0 — project foundation.** The application skeleton,
> database migrations, containerized environment and CI pipeline are in place.
> No business functionality is implemented yet. See the [roadmap](#roadmap).
>
> TPS is a portfolio project. It uses simulated financial providers and is not
> certified or intended to process real customer funds.

## Tech stack

| Area | Technology |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4 (Spring Web MVC, Spring Data JPA) |
| Database | PostgreSQL 18, Flyway migrations |
| Testing | JUnit 5, Spring Boot Test, Testcontainers |
| Build | Maven (wrapper included) |
| Infrastructure | Docker, Docker Compose, GitHub Actions |

## Architecture

TPS is a **modular monolith**: a single Spring Boot application divided into
business modules with enforced boundaries. Architectural decisions are
documented as ADRs in [docs/adr](docs/adr/README.md).

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
│   └── adr/              Architecture Decision Records
├── .github/workflows/    CI pipelines (GitHub Actions)
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

On startup the application connects to PostgreSQL and applies all Flyway
migrations.

### Run everything in containers

```bash
docker compose --profile app up --build
```

This builds the backend image and runs it with the `prod` profile against the
PostgreSQL container, on <http://localhost:8080>.

### Run the tests

```bash
cd backend
./mvnw verify
```

Tests start a real PostgreSQL instance with Testcontainers, so Docker must be
running. No local database is required.

## Configuration

| Profile | When | Database connection |
|---|---|---|
| `dev` (default) | Local development | Local Docker PostgreSQL (defaults in `application-dev.yml`) |
| `prod` | Docker image, deployments | Required environment variables, no defaults |

Database environment variables (Spring Boot standard):
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
`SPRING_DATASOURCE_PASSWORD`.

## Continuous integration

Every push and pull request to `main` or `develop` runs
[Backend CI](.github/workflows/backend-ci.yml): it builds the project with
Java 25 and runs the full test suite, including database tests against
PostgreSQL. Pull requests cannot be merged unless CI passes.

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| 0 | Project foundation and architecture | ✅ Done |
| 1 | Core backend and API foundation | Planned |
| 2 | Identity, security and access control | Planned |
| 3 | Accounts and financial ledger | Planned |
| 4 | Transaction processing engine | Planned |
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
