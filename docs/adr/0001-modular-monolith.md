# ADR 0001 — Modular monolith with Spring Modulith

- **Status:** Accepted
- **Date:** 2026-11-21

## Context

TPS covers several business areas (identity, accounts, ledger, transactions,
transfers, payments, provider integrations, reconciliation, audit). These areas
need clear boundaries so the codebase stays maintainable, but the project is
developed by a single engineer and financial operations rely heavily on local
ACID database transactions across areas (e.g. a transfer and its ledger entries
must commit atomically).

Microservices would add network calls, distributed transactions, and
operational overhead without a real requirement that justifies them.

## Decision

TPS is built as a **modular monolith**: one deployable Spring Boot application,
internally split into business modules.

- Code is organized **by business module** (package-by-feature), not by
  technical layer. Each module is a direct sub-package of
  `io.github.kenercartagena.tps`, for example:

  ```
  io.github.kenercartagena.tps
  ├── accounts/
  ├── ledger/
  ├── transactions/
  └── ...
  ```

- Each module exposes a small public API (in its top-level package) and keeps
  its internals in sub-packages (e.g. `internal`, `web`, `persistence`) that
  other modules must not use.
- Module boundaries are **verified automatically** with
  [Spring Modulith](https://spring.io/projects/spring-modulith): a test fails
  the build if one module reaches into another module's internals or if module
  dependencies form a cycle. Spring Modulith is introduced in Phase 1, together
  with the first module structure.

## Consequences

- Positive: simple deployment and local development; atomic database
  transactions across modules; boundaries enforced by tests, not only by
  convention; a module can later be extracted into a service if a real need
  appears.
- Negative: all modules share one database and one release cycle; discipline is
  required to keep module APIs small.
