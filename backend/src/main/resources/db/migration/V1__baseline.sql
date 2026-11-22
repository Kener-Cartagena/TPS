-- V1: Baseline migration.
--
-- Establishes Flyway version history for the TPS schema. Business tables
-- (accounts, ledger, transactions, ...) are added by later migrations in the
-- phase that introduces each module.
--
-- Conventions for every migration in this project:
--   * Never edit a migration that has been merged; add a new one instead.
--   * File name: V<version>__<snake_case_description>.sql
--   * Integrity rules (NOT NULL, CHECK, FK, UNIQUE) belong in the database,
--     not only in Java code.

COMMENT ON SCHEMA public IS 'Transaction Processing System (TPS) schema';
