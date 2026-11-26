# ADR 0007 — Transaction engine and idempotency

- **Status:** Accepted
- **Date:** 2026-11-25

## Context

Phase 3 built the ledger and customer accounts, but nothing moved money through the
API. The transaction engine is where every financial operation (deposits and
withdrawals now, transfers and reversals in Phase 5, payments in Phase 6) gets
validated, executed and recorded. It must:

- never move money twice for one operation, even when a client retries after a
  network timeout and does not know whether the first request worked;
- never move money from or to an account whose status forbids it, even if the status
  changes at the same moment;
- never overdraw an account, however many operations race on it;
- leave a record of every outcome, including refusals, for audit;
- stay correct under concurrency without deadlocks.

## Decision

1. **One `transactions` module** on top of `accounts` and `ledger`. It owns the
   `Transaction` aggregate (type, status, amount, accounts, idempotency key, outcome)
   and its immutable status history. What is specific to a type is an
   `OperationHandler` (Strategy pattern) that says how the type moves money; the
   engine does everything else the same way for every type. Every type must have
   exactly one handler, or the application does not start.
2. **Scope of Phase 4: deposits and withdrawals** (cash at a branch, against the
   settlement account of the currency). Transfers and reversals are Phase 5, payments
   Phase 6.
3. **Lifecycle.** Seven statuses: `CREATED`, `PENDING`, `PROCESSING`, `COMPLETED`,
   `FAILED`, `REJECTED`, `REVERSED`, with the transitions fixed in
   `TransactionStatus`, and per type the statuses it can reach
   (`TransactionType.uses`). `REJECTED` is a business rule that refused the operation
   (deterministic: the same request gets the same answer); `FAILED` is a technical
   problem while processing (Phase 6, e.g. a provider timeout). Deposits and
   withdrawals go `CREATED → PROCESSING → COMPLETED | REJECTED`. Every transition is
   stored in `transaction_status_changes`.
4. **One database transaction per operation.** The transaction row is inserted, the
   accounts locked, the rules checked, the journal entry posted and the outcome stored
   in a single database transaction. There is no intermediate commit, so an operation
   can never be left half done (no "stuck in PROCESSING" state to recover).
5. **Rejections are decided before writing, and committed.** An exception that leaves
   a `@Transactional` method of another bean (such as `Ledger.post`) marks the whole
   transaction *rollback-only*, even if the caller catches it. So the engine checks
   every rule itself, in plain Java, on data it has already locked: status, currency,
   available funds. A failed rule turns the transaction `REJECTED` with its error code,
   and the database transaction commits normally. The ledger's own checks remain as a
   second line of defense. Any *unexpected* failure (an account that does not exist, a
   lock timeout, a bug) rolls everything back: nothing is recorded, and retrying is
   safe.
6. **Idempotency keys.** Every request that moves money must carry an
   `Idempotency-Key` header (1 to 64 letters, digits, `.`, `_`, `:`, `-`; a UUID is
   recommended), following the semantics of the IETF draft
   *The Idempotency-Key HTTP Header Field*. Keys are unique per user
   (`UNIQUE (created_by, idempotency_key)`) and never expire, because transactions are
   never deleted. The stored result is the transaction itself, not a copy of the HTTP
   response; the response is rebuilt from it:
   - same key and same request → the first result again (same status code and body),
     with `Idempotent-Replayed: true`, and nothing is processed;
   - same key, different request → `422 IDEMPOTENCY_KEY_REUSED`;
   - missing or malformed key → `400`.

   "Same request" is decided by a SHA-256 *fingerprint* of the parsed request (type,
   amount with the currency's scale, currency, accounts, description), each part
   length-prefixed so different requests cannot collide by concatenation. Spacing,
   field order or `10` versus `10.00` do not change it.
7. **Concurrent duplicates wait for the first.** The transaction row, with its key, is
   inserted and flushed before any balance is locked. A second request with the same
   key blocks on the unique index until the first commits, then fails with a unique
   violation; the engine (deliberately *not* `@Transactional`) then reads the winner in
   a new transaction and returns its result. If the first one rolled back, the second
   simply proceeds. Money moves exactly once; there is no "in progress" state to
   expose.
8. **One global lock order.** Customer account rows first, with `SELECT ... FOR SHARE`
   in ascending id order (`Accounts.lockForOperation`), then their ledger accounts with
   `FOR UPDATE` in ascending id order (`Ledger.lockForPosting`, the same order
   `Ledger.post` uses). Many operations can share the lock on an account, but a status
   change (`FOR UPDATE` on the account, then its ledger account: the same order) waits
   for them, and they wait for it. So no withdrawal debits an account suspended after
   it checked the status, and nothing deadlocks. Locking the ledger accounts together,
   sorted, matters: settlement accounts have fixed ids that sort before every customer
   ledger account, and locking the customer's first would invert the order.
9. **Lock timeout.** Each operation sets PostgreSQL's `lock_timeout` for its own
   transaction (`tps.transactions.lock-timeout`, 5 seconds). An operation that waits
   longer gives up with `409 CONCURRENT_MODIFICATION`; everything is rolled back, so
   the client can retry. Without it, one stuck operation would make every request on
   that account wait forever, each holding a pooled connection.
10. **What each account status allows.** `ACTIVE`: debits and credits. `SUSPENDED`:
    neither (`ACCOUNT_SUSPENDED`): an account under investigation is frozen in both
    directions. `CLOSED`: neither (`ACCOUNT_CLOSED`). The rules live in the accounts
    module (`AccountStatus.allowsDebits/allowsCredits`), exposed through its new public
    API (`Accounts`, `OperableAccount`); no other module touches `accounts.domain`.
11. **No holds yet.** Every operation completes at once, so nothing is ever reserved:
    the available balance equals the ledger balance. The single place that computes
    available funds (`AvailableFunds`) is where Phase 6 subtracts the amounts held by
    pending payments.
12. **API.** `POST /api/v1/deposits` (`DEPOSIT_EXECUTE`) and `POST /api/v1/withdrawals`
    (`WITHDRAWAL_EXECUTE`), operators only; `GET /api/v1/transactions`, `/{id}` and
    `/{id}/status-history` (`TRANSACTION_READ`), operators and auditors. A completed
    operation is `201 Created` with the transaction; a rejected one is `422` Problem
    Details with the rule's code and the `transactionId`, so it can be looked up.
13. **The database guards the same rules** (migration `V6`): one type-specific shape of
    accounts per row, positive amounts, a journal entry exactly when completed, a
    reason exactly when rejected or failed, the unique key, and triggers that reject
    changing what was requested, changing a final status, or deleting anything.
14. **API contract in CI.** The OpenAPI document generated from the code is compared
    with a reviewed snapshot (`docs/api/openapi.json`) by a new CI job; an API change
    that does not update the snapshot fails the pull request.

## Consequences

- Positive: retries are safe by construction; every outcome, including refusals, is
  recorded and explained; operations on one account are serialized only where they
  must be (shared locks on the account, exclusive locks on balances); new transaction
  types are new handlers, not changes to the engine; the database rejects what the
  application would never do, even through direct SQL; API changes are always visible
  in review.
- Negative: a rejected operation is a write (a row and its history), so a client
  hammering an empty account fills the table; rate limiting is Phase 8 material.
  Every deposit and withdrawal of a currency locks its settlement account, a
  serialization point acceptable at this project's volumes (see ADR 0006). Business
  rules are checked twice, by the engine and again by the ledger and the database;
  they must be kept in step, and the tests cover all three. The OpenAPI snapshot must
  be regenerated whenever the API changes on purpose.
