# REST API conventions

These conventions apply to every endpoint of the TPS public API. The reasoning
behind them is recorded in
[ADR 0004](adr/0004-api-conventions-and-error-format.md).

## URLs

| Rule | Example |
|---|---|
| Every endpoint lives under the versioned base path `/api/v1` (`ApiPaths.API_V1`) | `/api/v1/accounts` |
| Resources are nouns in the plural | `/api/v1/transfers`, not `/api/v1/transfer` or `/api/v1/createTransfer` |
| Multi-word path segments use kebab-case | `/api/v1/payment-providers` |
| A single resource is addressed by its identifier | `/api/v1/accounts/{accountId}` |
| Sub-resources express ownership | `/api/v1/accounts/{accountId}/transactions` |
| Actions that are not CRUD are sub-resources, named as nouns, created with `POST` | `POST /api/v1/transfers/{transferId}/reversals` |
| Query parameters use camelCase | `?pageSize=20&status=COMPLETED` |

A breaking change to the contract (removing a field, changing its meaning or
type) requires a new version (`/api/v2`). Adding optional fields is not a
breaking change.

## Authentication

Every endpoint requires `Authorization: Bearer <access token>` unless it is
explicitly public (login, health, API documentation). Tokens are obtained with
`POST /api/v1/auth/tokens`. Operations are authorized by permission. The
security model, the permission matrix and the configuration are in
[security.md](security.md).

## HTTP methods and status codes

| Method | Use | Success status |
|---|---|---|
| `GET` | Read; never changes state | `200 OK` |
| `POST` | Create a resource or start an operation | `201 Created` + `Location` header, or `202 Accepted` for asynchronous processing |
| `PUT` | Replace a resource completely | `200 OK` |
| `PATCH` | Partial update | `200 OK` |
| `DELETE` | Remove (rare: financial records are never deleted) | `204 No Content` |

| Error status | When | `code` |
|---|---|---|
| `400 Bad Request` | Validation failed or the body is malformed | `VALIDATION_FAILED`, `MALFORMED_REQUEST` |
| `401 Unauthorized` | Missing or invalid token, or failed login | `AUTHENTICATION_REQUIRED`, `INVALID_CREDENTIALS` |
| `403 Forbidden` | Valid token, missing permission | `ACCESS_DENIED` |
| `404 Not Found` | Unknown endpoint or resource (authenticated callers) | `NOT_FOUND` or a module code, e.g. `USER_NOT_FOUND` |
| `405`, `406`, `415` | Wrong method, `Accept` or `Content-Type` | `METHOD_NOT_ALLOWED`, `NOT_ACCEPTABLE`, `UNSUPPORTED_MEDIA_TYPE` |
| `409 Conflict` | Conflicts with the current state: duplicate, concurrent modification | module code, e.g. `USERNAME_ALREADY_EXISTS`; `CONCURRENT_MODIFICATION` when another request changed the resource at the same time (read it again and retry) |
| `422 Unprocessable Content` | Valid request that breaks a business rule | module code, e.g. `INSUFFICIENT_FUNDS` |
| `500 Internal Server Error` | Unexpected failure | `INTERNAL_ERROR` |

## Request and response bodies

- JSON only (`application/json`). Property names use **camelCase**.
- **No envelope**: a successful response returns the resource itself. The HTTP
  status code tells whether the request succeeded.
  ```json
  { "accountId": "...", "status": "ACTIVE", "currency": "HNL" }
  ```
- Lists that can grow are paginated (see [Pagination](#pagination)).
- Enumerations are strings in `UPPER_SNAKE_CASE` (`"COMPLETED"`).
- Optional values that are absent are omitted or `null`, never empty strings.

## Pagination

List endpoints accept two query parameters:

| Parameter | Meaning | Default | Allowed |
|---|---|---|---|
| `page` | Zero-based page number | `0` | `>= 0` |
| `size` | Items per page | `20` | `1` to `100` |

The sort order is fixed by each endpoint and documented in its OpenAPI
description (e.g. users are sorted by username). Clients cannot sort by
arbitrary fields, which would let them probe the ordering of values they cannot
read.

The response is Spring Data's stable page format:

```json
{
  "content": [ { "id": "..." } ],
  "page": { "size": 20, "number": 0, "totalElements": 42, "totalPages": 3 }
}
```

## Data types

| Type | Format | Example |
|---|---|---|
| Date-time | ISO-8601, always UTC (`Z`) | `"2026-11-23T18:04:12.345Z"` |
| Date | ISO-8601 | `"2026-11-23"` |
| Money amount | **Decimal string**, never a JSON number or floating point | `"1250.50"` |
| Currency | ISO-4217 code | `"HNL"`, `"USD"` |
| Identifiers | Opaque strings (UUID) | `"3f2a9c1e-..."` |

Amounts are strings so that no client parses them as binary floating point
(`0.1 + 0.2 != 0.3`). In Java they are `shared.money.Money` (`BigDecimal` plus
currency), always with the currency's 2 decimal places (`"10.00"`); an amount with
more decimals is rejected, never rounded. Supported currencies: `HNL`, `USD`.

## Errors: RFC 9457 Problem Details

Every error response has the content type `application/problem+json` and this
shape:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Request validation failed.",
  "instance": "/api/v1/transfers",
  "code": "VALIDATION_FAILED",
  "requestId": "3f2a9c1e-6b1d-4a57-9f1e-2a4c8d0b7e11",
  "timestamp": "2026-11-23T18:04:12.345Z",
  "errors": [
    { "field": "amount", "message": "must be greater than 0" }
  ]
}
```

| Property | Meaning |
|---|---|
| `type`, `title`, `status`, `detail`, `instance` | Standard RFC 9457 members |
| `code` | **Stable, machine-readable error code.** Clients must branch on this, never on `detail` |
| `requestId` | Same value as the `X-Request-Id` response header; quote it when reporting a problem |
| `timestamp` | When the error occurred (UTC) |
| `errors` | Only for `VALIDATION_FAILED`: one entry per invalid field or parameter |

Rules:

- The rejected value is **never** echoed back, so sensitive input cannot leak.
- A `500` never contains exception messages or stack traces. The details are
  only in the server logs, under the same `requestId`.
- Error codes are part of the contract: once released they are never renamed.

### Throwing errors from a module

Modules do not build error responses. They throw one of the exception
categories from `shared.error`, with a module-specific `ErrorCode`:

| Exception | Status |
|---|---|
| `ResourceNotFoundException` | `404` |
| `ConflictException` | `409` |
| `BusinessRuleViolationException` | `422` |
| `AuthenticationFailedException` | `401` |


```java
enum AccountErrorCode implements ErrorCode {
    ACCOUNT_NOT_FOUND, ACCOUNT_SUSPENDED;

    @Override
    public String code() { return name(); }
}

throw new ResourceNotFoundException(AccountErrorCode.ACCOUNT_NOT_FOUND,
        "Account '%s' was not found.".formatted(accountId));          // -> 404

throw new BusinessRuleViolationException(AccountErrorCode.ACCOUNT_SUSPENDED,
        "The account is suspended and cannot be debited.");           // -> 422
```

The exception message becomes the `detail` of the response, so it must be
written for API clients and must not contain sensitive data.

## Request id

Every response carries an `X-Request-Id` header. A client may send its own
`X-Request-Id` (1 to 64 characters: letters, digits, `.`, `_`, `-`), and it is
reused. Otherwise the server generates a UUID. The id appears in every log line
of that request and in error responses.

## Documentation

The OpenAPI 3 document is generated from the code:

- Swagger UI: `/swagger-ui.html`
- OpenAPI JSON: `/v3/api-docs`

Only paths under `/api/**` are documented. The documentation is public; to try
protected operations in Swagger UI, log in and paste the token with
**Authorize**.
