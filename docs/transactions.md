# Transactions

How money moves in TPS: the transaction engine, its lifecycle, idempotent retries, and
the rules that keep it consistent under concurrency. The reasoning behind these choices
is in [ADR 0007](adr/0007-transaction-engine-and-idempotency.md); accounts and the
ledger are described in [ledger.md](ledger.md).

## In one picture

```
 POST /deposits, /withdrawals  (Idempotency-Key)
          │
          ▼
 TransactionEngine        same key seen before? → replay the stored result
          │ new key
          ▼
 TransactionProcessor     ONE database transaction:
   1. lock_timeout 5s
   2. lock customer accounts      FOR SHARE   (accounts module)
   3. insert transaction CREATED  (unique key: a duplicate waits here)
   4. PROCESSING: lock ledger accounts FOR UPDATE, check the rules
   5a. a rule fails  → REJECTED (code)          ─┐
   5b. all rules pass → post journal entry,      ├─ commit
                        COMPLETED                ─┘
```

A **transaction** records one operation: what was asked (type, amount, accounts,
description), by whom, with which idempotency key, and how it ended. Transactions are
never deleted, and what was asked never changes.

| Type | Money | Journal entry |
|---|---|---|
| `DEPOSIT` | enters the destination account (cash at a branch) | debit `SETTLEMENT-<currency>`, credit the customer account |
| `WITHDRAWAL` | leaves the source account (cash at a branch) | debit the customer account, credit `SETTLEMENT-<currency>` |

Transfers and reversals arrive in Phase 5, payments in Phase 6.

## Lifecycle

```
CREATED ──→ PROCESSING ──→ COMPLETED ──→ REVERSED   (Phase 5)
  │  │          │  └─────→ REJECTED
  │  │          └────────→ FAILED                   (Phase 6)
  │  └─→ PENDING ──→ PROCESSING, REJECTED, FAILED   (Phase 6)
  └────→ REJECTED
```

| Status | Meaning |
|---|---|
| `CREATED` | Recorded and accepted for processing |
| `PENDING` | Waiting for something outside TPS, e.g. a payment provider (Phase 6) |
| `PROCESSING` | Being executed: accounts locked, rules being checked |
| `COMPLETED` | The money moved; `journalEntryId` is the ledger entry |
| `REJECTED` | A business rule refused it; no money moved; `rejectionCode` says which |
| `FAILED` | A technical problem while processing; no money moved (Phase 6) |
| `REVERSED` | Undone by a reversal (Phase 5) |

Deposits and withdrawals are processed at once: `CREATED → PROCESSING → COMPLETED` or
`→ REJECTED`, in the same database transaction. Every step is stored in the status
history (`GET /api/v1/transactions/{id}/status-history`), oldest first.

## Rules

Checked in this order for each customer account of the operation, after it is locked:

| Rule | Error code (422) |
|---|---|
| The account exists | `404 ACCOUNT_NOT_FOUND` (nothing is recorded) |
| A suspended account receives and sends nothing | `ACCOUNT_SUSPENDED` |
| A closed account receives and sends nothing | `ACCOUNT_CLOSED` |
| The currency is the account's currency | `CURRENCY_MISMATCH` |
| A debited account has the funds | `INSUFFICIENT_FUNDS` |

What each account status allows:

| Status | Debit (money out) | Credit (money in) |
|---|:-:|:-:|
| `ACTIVE` | ✔ | ✔ |
| `SUSPENDED` | ✘ | ✘ |
| `CLOSED` | ✘ | ✘ |

A rejection is recorded: the transaction is `REJECTED` with the code and an
explanation, and the response is `422` Problem Details with the same `code` plus the
`transactionId`:

```json
{
  "type": "about:blank",
  "title": "Unprocessable Content",
  "status": 422,
  "detail": "The account does not have enough funds for this operation.",
  "instance": "/api/v1/withdrawals",
  "code": "INSUFFICIENT_FUNDS",
  "transactionId": "0193b2d0-...",
  "requestId": "3f2a9c1e-...",
  "timestamp": "2026-11-25T15:04:12.345Z"
}
```

Request validation (`400 VALIDATION_FAILED`, nothing recorded): `amount` is a positive
decimal string with at most 2 decimals (`"1250.50"`), `currency` is `HNL` or `USD`,
`description` is optional (1 to 200 characters, not blank).

## Idempotency: safe retries

A client that sends a deposit and gets no answer (a timeout, a dropped connection)
cannot know whether the money moved. It must be able to **retry without moving it
twice**. Every request that moves money therefore carries an `Idempotency-Key` header:
a value the client generates once per operation (a UUID is recommended) and sends
again, unchanged, on every retry of that operation.

| Request | Response |
|---|---|
| New key | Processed: `201` (completed) or `422` (rejected) |
| Same key, same request | **The first result again**, same status and body, plus `Idempotent-Replayed: true`. Nothing is processed |
| Same key, different request | `422 IDEMPOTENCY_KEY_REUSED` |
| Same key, sent twice at the same time | The second waits for the first, then gets its result. The money moves once |
| No key, or a malformed one | `400` |

- Keys are **unique per user**: two operators may use the same value independently.
- Keys **never expire**: the stored result is the transaction itself, which is kept forever.
- "Same request" means the same type, account, amount (`10` and `10.00` are the same),
  currency and description, compared through a SHA-256 fingerprint stored with the
  transaction.
- A retry of a *rejected* request gets the same rejection, even if the account has the
  funds now. To try again after fixing the cause, use a **new key**: it is a new
  operation.
- If a request fails unexpectedly (`409` lock timeout, `500`), nothing was recorded, and
  retrying with the same key is safe.

### Example: a deposit and its retry (as an operator)

```bash
curl -i -X POST http://localhost:8080/api/v1/deposits \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -H "Idempotency-Key: 8e03978e-40d5-43e8-bc93-6894a57f9324" \
  -d '{"accountId": "<account id>", "amount": "1500.00", "currency": "HNL",
       "description": "Salary"}'
```

```http
HTTP/1.1 201 Created
Location: http://localhost:8080/api/v1/transactions/0193b2d0-...
```

```json
{
  "id": "0193b2d0-...",
  "type": "DEPOSIT",
  "status": "COMPLETED",
  "amount": "1500.00",
  "currency": "HNL",
  "sourceAccountId": null,
  "destinationAccountId": "<account id>",
  "description": "Salary",
  "journalEntryId": "0193b2d0-...",
  "rejectionCode": null,
  "rejectionDetail": null,
  "createdBy": "0193a1b2-...",
  "createdAt": "2026-11-25T15:04:12.345Z",
  "updatedAt": "2026-11-25T15:04:12.351Z",
  "processedAt": "2026-11-25T15:04:12.351Z"
}
```

Sending exactly the same request again returns the same `201` and body, with
`Idempotent-Replayed: true`, and the balance does not change.

## Concurrency

Every operation locks, **always in this order**:

1. its customer accounts, `SELECT ... FOR SHARE`, in ascending id order;
2. their ledger accounts (and the settlement account), `SELECT ... FOR UPDATE`, in
   ascending id order.

| Situation | What happens |
|---|---|
| Many withdrawals on one account | Serialized on its ledger account: each sees the balance left by the previous one. The account is never overdrawn; the refused ones are `REJECTED INSUFFICIENT_FUNDS` |
| A suspension while withdrawals run | The suspension (`FOR UPDATE` on the account) waits for the withdrawals holding it; later withdrawals see `SUSPENDED`. None completes after the suspension |
| Deposits and withdrawals on the same accounts | Same lock order everywhere, so they wait for each other but never deadlock |
| A lock held too long by someone else | After `tps.transactions.lock-timeout` (5 s) the operation gives up: `409 CONCURRENT_MODIFICATION`, nothing recorded, safe to retry |

`TransactionConcurrencyTests` runs each of these with 8 threads against a real
PostgreSQL database.

## Database guarantees (migration `V6`)

| Rule | How |
|---|---|
| A deposit has only a destination, a withdrawal only a source | `CHECK` |
| Amounts are positive | `CHECK` |
| A journal entry exactly when `COMPLETED` (or `REVERSED`) | `CHECK` |
| A rejection code exactly when `REJECTED` (or `FAILED`) | `CHECK` |
| One transaction per user and idempotency key | `UNIQUE (created_by, idempotency_key)` |
| What was requested never changes; a final status is final | trigger `tps_guard_transaction_update` |
| Transactions and their history are never deleted; history never changes | triggers |

## Endpoints

| Method and path | Permission | Description |
|---|---|---|
| `POST /api/v1/deposits` | `DEPOSIT_EXECUTE` | Deposit cash into an account (requires `Idempotency-Key`) |
| `POST /api/v1/withdrawals` | `WITHDRAWAL_EXECUTE` | Withdraw cash from an account (requires `Idempotency-Key`) |
| `GET /api/v1/transactions?accountId=&type=&status=` | `TRANSACTION_READ` | Transactions, newest first; `accountId` matches either side |
| `GET /api/v1/transactions/{transactionId}` | `TRANSACTION_READ` | A transaction |
| `GET /api/v1/transactions/{transactionId}/status-history` | `TRANSACTION_READ` | Its statuses, oldest first |

Operators deposit, withdraw and read; auditors read; administrators have no access
(see [security.md](security.md)).

## Known limitations

- **No holds.** Operations complete at once, so the available balance equals the
  ledger balance. Pending payments (Phase 6) will hold their amount.
- **No transfers or reversals yet** (Phase 5), **no payments** (Phase 6).
- **Rejected requests are stored.** A client that keeps retrying with new keys
  against an empty account adds a row each time; rate limiting is not implemented yet.
- **Every operation locks the settlement account of its currency**, a serialization
  point acceptable at this project's volumes (see ADR 0006).
