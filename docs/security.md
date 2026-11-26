# Security model

How users authenticate to TPS and what each of them may do. The reasoning
behind these choices is in
[ADR 0005](adr/0005-authentication-and-authorization.md).

## Users and roles

Users are the people who operate TPS (back-office staff), not customers. Each
user has exactly one role:

| Role | Purpose |
|---|---|
| `SYSTEM_ADMIN` | Manages users and their access. Does **not** execute financial operations. |
| `OPERATOR` | Executes day-to-day financial operations. |
| `AUDITOR` | Read-only access for audit and compliance, including who has access. |

Users are never deleted, only disabled, so every recorded action keeps pointing
to a real user.

## Permissions

Endpoints check **permissions**, not roles. Each role grants a fixed set of
permissions, defined in
[`Role.java`](../backend/src/main/java/io/github/kenercartagena/tps/identity/domain/Role.java)
and pinned by
[`AuthorizationMatrixTests`](../backend/src/test/java/io/github/kenercartagena/tps/identity/web/AuthorizationMatrixTests.java).

A permission is added in the phase that introduces the operation it protects:

| Permission | SYSTEM_ADMIN | OPERATOR | AUDITOR | Phase |
|---|:-:|:-:|:-:|---|
| `USER_READ` | ✔ | | ✔ | 2 ✅ |
| `USER_MANAGE` | ✔ | | | 2 ✅ |
| `ACCOUNT_READ` | | ✔ | ✔ | 3 ✅ |
| `ACCOUNT_MANAGE` | | ✔ | | 3 ✅ |
| `LEDGER_READ` | | | ✔ | 3 ✅ |
| `TRANSACTION_READ` | | ✔ | ✔ | 4 ✅ |
| `DEPOSIT_EXECUTE` | | ✔ | | 4 ✅ |
| `WITHDRAWAL_EXECUTE` | | ✔ | | 4 ✅ |
| `TRANSFER_EXECUTE` | | ✔ | | 5 |
| `REVERSAL_EXECUTE` | | ✔ | | 5 |
| `PAYMENT_EXECUTE` | | ✔ | | 6 |
| `RECONCILIATION_RUN` | | ✔ | | 7 |
| `RECONCILIATION_READ` | | ✔ | ✔ | 7 |
| `AUDIT_READ` | | | ✔ | 7 |

`LEDGER_READ` (internal accounts, journal entries, ledger verification) is for
auditors only: operators work with customer accounts, and their balances and
movements, through `ACCOUNT_READ`.

Rules enforced for every caller:

- Administrators cannot change their own role or status (`SELF_MODIFICATION_NOT_ALLOWED`).
- At least one active `SYSTEM_ADMIN` always remains (`LAST_ACTIVE_ADMIN`).

## Endpoints

| Method and path | Access | Description |
|---|---|---|
| `POST /api/v1/auth/tokens` | public | Log in; returns an access token |
| `GET /api/v1/users/me` | authenticated | My profile |
| `PUT /api/v1/users/me/password` | authenticated | Change my password (requires the current one) |
| `POST /api/v1/users` | `USER_MANAGE` | Create a user |
| `GET /api/v1/users` | `USER_READ` | List users (paginated, sorted by username) |
| `GET /api/v1/users/{userId}` | `USER_READ` | Get a user |
| `PUT /api/v1/users/{userId}/role` | `USER_MANAGE` | Change a user's role |
| `PUT /api/v1/users/{userId}/status` | `USER_MANAGE` | Activate (also unlocks) or disable a user |

Account holders, accounts and the ledger (`ACCOUNT_READ`, `ACCOUNT_MANAGE`,
`LEDGER_READ`): see the endpoint table in [ledger.md](ledger.md#endpoints).
Deposits, withdrawals and transactions (`DEPOSIT_EXECUTE`, `WITHDRAWAL_EXECUTE`,
`TRANSACTION_READ`): see [transactions.md](transactions.md#endpoints).

Also public: `/actuator/health/**`, `/actuator/info`, `/v3/api-docs`,
`/swagger-ui.html`. Everything else requires a token; unknown URLs answer `401`
to anonymous callers.

## Authentication

1. Log in:
   ```bash
   curl -X POST http://localhost:8080/api/v1/auth/tokens \
     -H "Content-Type: application/json" \
     -d '{"username": "admin", "password": "admin-local-password"}'
   ```
   ```json
   {
     "accessToken": "eyJhbGciOi...",
     "tokenType": "Bearer",
     "expiresIn": 1800,
     "expiresAt": "2026-11-24T10:30:00Z"
   }
   ```
2. Send the token with every request:
   ```bash
   curl http://localhost:8080/api/v1/users/me -H "Authorization: Bearer eyJhbGciOi..."
   ```
3. In Swagger UI (`/swagger-ui.html`): run the login operation, copy
   `accessToken`, click **Authorize** and paste it.

The token is a JWT signed with HMAC-SHA256. Its claims are `sub` (the user id),
`username`, `role`, `iss`, `iat`, `exp` and `jti`. It is valid for 30 minutes.

### Local credentials

With the `dev` profile (and `docker compose --profile app up`), the first start
against an empty database creates the administrator **`admin`** /
**`admin-local-password`**. These values and the local JWT secret are public in
the repository: they are only for your machine.

## Passwords and login protection

- Hashed with bcrypt through Spring Security's `DelegatingPasswordEncoder`; the
  hash is stored with its algorithm id (`{bcrypt}...`). Passwords are never logged
  or returned.
- Policy (NIST SP 800-63B): 12 to 64 characters, any characters including spaces,
  no composition rules, at most 72 bytes, not equal to the username.
- After **5** consecutive failed logins the account is locked for **15 minutes**.
  An administrator can unlock it earlier by setting its status to `ACTIVE`.
- Every login failure returns the same response (`401 INVALID_CREDENTIALS`,
  "Invalid username or password."), whatever the reason.

## Error responses

Security errors use the standard [Problem Details format](api-conventions.md#errors-rfc-9457-problem-details):

| Status | `code` | When |
|---|---|---|
| `401` | `INVALID_CREDENTIALS` | Login failed |
| `401` | `AUTHENTICATION_REQUIRED` | No token, or the token is malformed, tampered with, expired or from another issuer |
| `403` | `ACCESS_DENIED` | Valid token, but the role lacks the permission |

A `401` carries the RFC 6750 header `WWW-Authenticate: Bearer`, or
`Bearer error="invalid_token"` when a token was sent but rejected.

## Configuration

| Property | Environment variable | Default |
|---|---|---|
| `tps.security.jwt.secret` | `TPS_SECURITY_JWT_SECRET` | none: **required**, Base64, at least 32 bytes |
| `tps.security.jwt.issuer` | `TPS_SECURITY_JWT_ISSUER` | `tps` |
| `tps.security.jwt.expiration` | `TPS_SECURITY_JWT_EXPIRATION` | `30m` |
| `tps.security.login.max-attempts` | `TPS_SECURITY_LOGIN_MAXATTEMPTS` | `5` |
| `tps.security.login.lockout` | `TPS_SECURITY_LOGIN_LOCKOUT` | `15m` |
| `tps.security.bootstrap.admin.username` | `TPS_SECURITY_BOOTSTRAP_ADMIN_USERNAME` | none |
| `tps.security.bootstrap.admin.password` | `TPS_SECURITY_BOOTSTRAP_ADMIN_PASSWORD` | none |

Generate a secret with `openssl rand -base64 32`. The bootstrap administrator is
only created when the database has no users at all; afterwards the variables are
ignored and can be removed.

## Known limitations

These are deliberate trade-offs for v1, recorded so they are not mistaken for
oversights:

- **Tokens cannot be revoked.** A disabled user, or one whose role changed, keeps
  their current access until the token expires (at most 30 minutes).
- **No refresh tokens.** Users log in again when the token expires. To be designed
  with the dashboard (Phase 10).
- **No rate limiting per IP address**, only the per-account lockout (Phase 8).
- **No persistent audit trail yet.** Security events (logins, user changes) are
  written to the logs with user ids; the audit module stores them later. Account
  status changes already have their own immutable history (who, when, why).
- **No password reset by an administrator.** A user who forgets their password
  needs a new account for now.
