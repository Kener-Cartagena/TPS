# ADR 0004 — REST API conventions and error format

- **Status:** Accepted
- **Date:** 2026-11-23

## Context

Every module of TPS will expose REST endpoints. Without shared rules each
endpoint would invent its own URL style, success format and error format, and
clients would need special handling per endpoint. Financial APIs also need
errors that clients can handle programmatically (e.g. "insufficient funds"
versus "account not found"), and that never leak sensitive data or internal
details.

## Decision

1. **Versioned, resource-oriented URLs** under `/api/v1`, with plural nouns,
   kebab-case path segments and camelCase JSON. The full rules are in
   [api-conventions.md](../api-conventions.md).
2. **No response envelope** for successful responses: the body is the resource
   itself, and the HTTP status code carries the outcome. Pagination is defined
   when the first list endpoint is built (Phase 3).
3. **Errors use RFC 9457 Problem Details** (`application/problem+json`), the
   IETF standard natively supported by Spring Framework, extended with:
   - `code`: a stable, machine-readable error code,
   - `requestId`: correlates the response with the server logs,
   - `timestamp`: when the error occurred (UTC),
   - `errors`: field violations for validation failures.
4. **A single global exception handler** in the `shared` module produces every
   error response, including those raised by Spring MVC itself. Modules only
   throw exception categories (`ResourceNotFoundException` → 404,
   `BusinessRuleViolationException` → 422) with their own `ErrorCode`.
5. **Safe by default:** rejected values are never echoed, and unexpected errors
   return a generic `500` without exception messages or stack traces.
6. **Money is a decimal string** in JSON and `BigDecimal` in Java. Date-times are
   ISO-8601 in UTC.
7. **The OpenAPI document is generated from the code** (springdoc-openapi) and is
   published in every profile, including `prod`, because TPS is a public
   portfolio demo. Phase 2 revisits whether it stays public once authentication
   exists.

## Consequences

- Positive: clients handle every error the same way and branch on `code`; the
  format is a recognized standard; modules cannot produce inconsistent errors;
  every failure can be traced from the response to the logs through the request id.
- Negative: error codes become part of the public contract and cannot be
  renamed freely; exceptions must be mapped to a category up front.
