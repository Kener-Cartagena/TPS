# ADR 0006 — Double-entry ledger and customer accounts

- **Status:** Accepted
- **Date:** 2026-11-25

## Context

Every financial operation of TPS (deposits, transfers, payments, reversals) must
move money without creating or losing any, leave a trace that can be audited, and
stay correct when many requests touch the same accounts at the same time. Later
phases build the transaction engine (Phase 4), transfers and reversals (Phase 5)
and payments (Phase 6) on top of whatever is decided here, so this foundation has
to be right before any of them exists.

The usual shortcut, an `accounts.balance` column updated directly, cannot explain
a balance, cannot be verified, and loses updates under concurrency unless the code
is careful everywhere.

## Decision

1. **Two modules.** `ledger` is the general ledger and the lowest business
   module: it knows nothing about customers or transactions, and its allowed
   dependencies are declared, so Spring Modulith fails the build if it ever
   depends on a module above it. `accounts` owns holders and customer accounts and
   uses the ledger through its public facade `Ledger`. Each customer account is
   backed by its own ledger account, opened in the same database transaction.
2. **Double-entry bookkeeping.** Money only moves through a *journal entry*: two
   or more *postings* (debits and credits) in one currency whose totals are equal.
   Customer accounts are `LIABILITY` accounts (money TPS owes its customers); money
   enters and leaves through `ASSET` settlement accounts, one per currency, created
   by a migration with fixed ids.
3. **Balances on the normal side.** Each account type has a normal side (debit for
   `ASSET` and `EXPENSE`, credit for the others). Postings store a positive amount
   and a direction; the balance grows on the normal side, so a customer's balance
   is positive when TPS owes them money, as on a bank statement.
4. **Stored balance plus running balance.** The account's balance is stored, and
   each posting records the balance right after it (`balance_after`). Both are
   updated in the same database transaction as the postings. Reading a balance is
   one row; the history reads like a statement. The alternative, `SUM()` over all
   postings on every read, gets slower forever and gives no running balance.
5. **Pessimistic locking in a fixed order.** Posting an entry locks its accounts
   with `SELECT ... FOR UPDATE`, in ascending id order, until the transaction
   ends. Concurrent entries on the same account are serialized, so no update is
   lost, and two entries that touch the same accounts in opposite directions wait
   instead of deadlocking. Optimistic locking (`@Version`) would turn contention on
   a busy account into retries; it is kept for changes of account metadata (a lost
   race answers `409 CONCURRENT_MODIFICATION`). The rows are re-read after locking
   (`refresh`), so a balance read earlier in the same transaction is never reused.
6. **The rules are checked before anything changes.** `JournalEntry.post` (plain
   Java, unit-tested) validates the structure, the accounts and the resulting
   balances first, and only then applies them: a rejected entry leaves no trace.
   An account that does not allow a negative balance (every customer account)
   rejects the entry with `INSUFFICIENT_FUNDS`.
7. **The database enforces the same rules** (migrations `V3` and `V5`), so they
   hold even for SQL that bypasses the application:
   - a deferred constraint trigger, checked at `COMMIT`, rejects any entry with
     fewer than two postings, more than one currency, or debits different from
     credits;
   - triggers reject `UPDATE`, `DELETE` and `TRUNCATE` on journal entries, postings
     and account status history, and `DELETE` and `TRUNCATE` on ledger accounts,
     accounts and holders: financial
     records are **immutable**, and a mistake is corrected with a new entry;
   - a composite foreign key `(ledger_account_id, currency)` makes a posting use its
     account's currency; `CHECK (allow_negative OR balance >= 0)` forbids
     overdrafts; an account can only be closed with a zero balance;
   - account numbers, identity documents and statuses are checked with `CHECK`
     constraints.
8. **Verification from first principles.** `GET /api/v1/ledger/verification`
   recomputes every balance from the postings in one `REPEATABLE READ` snapshot
   and reports unbalanced entries, accounts whose stored balance or last running
   balance disagrees, and the debit/credit totals per currency. It detects a
   balance changed behind the application's back.
9. **Money.** `shared.money.Money` (`BigDecimal` + currency) with the scale fixed to
   the currency (2 decimals for `HNL` and `USD`); more decimals are rejected, never
   rounded. Columns are `NUMERIC(19,2)`; the API uses decimal strings. No currency
   conversion: an entry has one currency.
10. **Account numbers.** 12 digits: 10 from a PostgreSQL sequence plus 2 check
    digits (ISO 7064 MOD 97-10, as in the IBAN). Every single-digit typo and
    every swap of adjacent digits is detected; the database checks it too
    (`number % 97 = 1`).
11. **No HTTP endpoint posts to the ledger.** Money moves only through business
    operations (Phase 4 onwards), which add validation, idempotency and an audit
    trail. A generic "adjustment" endpoint would be a back door around all three.
    The ledger API is read-only, for auditors (`LEDGER_READ`).
12. **Account statuses.** `ACTIVE ⇄ SUSPENDED`, and either to `CLOSED`, which is
    final and requires a zero balance (it also closes the ledger account). Every
    change needs a reason and is recorded in an immutable history, opening
    included. Which operations each status allows is enforced by the transaction
    engine (Phase 4).

## Consequences

- Positive: money cannot be created or lost, by the application or by direct SQL;
  every balance is explained by its postings and can be verified at any time;
  concurrent operations are safe by construction, which the concurrency tests
  demonstrate; later phases only add business operations that post entries.
- Negative: every entry locks its accounts, so a very busy account (such as a
  settlement account) is a serialization point. That is acceptable at this
  project's volumes; high-volume ledgers shard such accounts or post to them
  asynchronously. Rules live in Java and in PL/pgSQL, and must be kept in step
  (the tests cover both). The verification scans the whole ledger; a production
  system would verify incrementally. Fixing a wrong entry always takes a
  compensating entry, never an edit.
