# ADR 0002 — PostgreSQL with Flyway migrations

- **Status:** Accepted
- **Date:** 2026-11-21

## Context

A financial ledger requires strong transactional guarantees, row-level locking
for concurrency control, and integrity enforced by the database itself (not
only by application code). The schema must evolve in a controlled, reviewable,
and reproducible way across every environment (local, CI, deployment).

## Decision

- **PostgreSQL 18** is the only supported database. Local development runs it
  in Docker (`docker-compose.yml`); tests run it with Testcontainers. The image
  version is pinned and kept identical in both places.
- **Flyway** owns the schema. Every change is a versioned SQL migration in
  `backend/src/main/resources/db/migration`, named
  `V<version>__<snake_case_description>.sql`.
- Merged migrations are **immutable**: fixes are made with a new migration.
- Hibernate runs with `ddl-auto: validate`; it never creates or alters tables.
- Integrity rules (`NOT NULL`, `CHECK`, `FOREIGN KEY`, `UNIQUE`) are declared in
  the database, in addition to validation in Java.
- Connection settings come from Spring Boot's standard environment variables
  (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
  `SPRING_DATASOURCE_PASSWORD`). Only the `dev` profile contains local,
  non-secret defaults.

## Consequences

- Positive: the same schema is produced everywhere from version-controlled SQL;
  tests exercise real PostgreSQL behavior (locking, constraints) rather than an
  in-memory substitute; schema changes are reviewed in pull requests.
- Negative: Docker is required to run the tests; SQL is PostgreSQL-specific, so
  switching databases would require rewriting migrations (accepted trade-off).
