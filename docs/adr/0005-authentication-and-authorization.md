# ADR 0005 — Authentication and authorization

- **Status:** Accepted
- **Date:** 2026-11-24

## Context

TPS executes financial operations, so every API call must be attributable to a
known person, and each person must only be able to do what their job requires.
The users are back-office staff (administrators, operators, auditors), not
customers. The project is free to run and deploy, is developed by one engineer,
and its repository is public. A React dashboard will consume the API in Phase 10.

ADR 0004 left open whether the API documentation stays public once
authentication exists.

## Decision

1. **Stateless bearer tokens (JWT) issued by TPS itself.** `POST /api/v1/auth/tokens`
   exchanges a username and password for a signed access token. Every other call
   sends `Authorization: Bearer <token>`. Verification uses Spring Security's
   OAuth 2.0 resource server support, so there is no custom token filter. No
   external identity provider (e.g. Keycloak), which would add infrastructure
   without a current need.
2. **HMAC-SHA256 (HS256)** with a secret of at least 256 bits from
   `TPS_SECURITY_JWT_SECRET`. The application refuses to start without it. TPS
   both issues and verifies tokens, so one shared secret is enough. RS256 becomes
   worthwhile if another service must verify tokens.
3. **Short-lived access tokens (30 minutes), no refresh tokens yet.** The token is
   self-contained: a role or status change takes effect at the next login, at the
   latest when the current token expires. Refresh tokens and their storage are
   designed with the dashboard in Phase 10.
4. **Permission-based authorization.** Each user has exactly **one role**; each
   role grants a fixed set of **permissions**, defined in code (`Role`). Every
   protected operation checks a permission with `@PreAuthorize`, never a role. The
   token carries only the role, which is expanded into permissions when the token
   is verified.
5. **Segregation of duties.** `SYSTEM_ADMIN` manages users and access but does not
   execute financial operations; `OPERATOR` executes them; `AUDITOR` can read but
   never changes anything. Administrators cannot change their own role or status,
   and the system always keeps at least one active administrator. The matrix is
   in [security.md](../security.md).
6. **No self-registration.** Only administrators create users. The first
   administrator is created at startup when the database has no users, from the
   `TPS_SECURITY_BOOTSTRAP_ADMIN_*` variables. A known password is never put in a
   migration, because the repository is public.
7. **Passwords** are hashed with Spring Security's `DelegatingPasswordEncoder`
   (bcrypt today; the algorithm id is stored with the hash, so it can be upgraded).
   The policy follows NIST SP 800-63B: 12 to 64 characters, no composition rules,
   at most 72 bytes (the bcrypt limit), not equal to the username.
8. **Brute-force protection.** After 5 consecutive failed logins the account is
   locked for 15 minutes (configurable); an administrator can unlock it early. Any
   login failure returns the same `401 INVALID_CREDENTIALS`, so the response never
   reveals whether a username exists, is locked or is disabled.
9. **Deny by default.** Only login, health/info and the API documentation are
   public. Unknown URLs answer `401` to anonymous callers.
10. **The API documentation stays public** (resolves ADR 0004): it contains no
    data, and every documented operation still requires a token. Swagger UI has an
    *Authorize* button.
11. **CSRF protection is disabled**: CSRF abuses credentials that the browser sends
    by itself (cookies); bearer tokens are added explicitly by the client. To be
    revisited if the dashboard uses cookies.
12. **401/403 use the standard error format** (ADR 0004): the security filter
    chain hands its failures to the global exception handler.

## Consequences

- Positive: a standard, widely understood mechanism built from Spring Security
  components; no session state, so the backend can scale horizontally; one place
  defines who may do what, and an executable test (`AuthorizationMatrixTests`)
  pins it; no extra infrastructure or cost.
- Negative: a token cannot be revoked before it expires; a disabled user keeps
  access for up to 30 minutes (mitigated by the short lifetime; a deny-list or
  refresh tokens can be added later). All instances must share the signing
  secret, and rotating it logs everyone out. There is no rate limiting per IP
  address yet (Phase 8).
