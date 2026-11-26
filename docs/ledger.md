# Accounts and ledger

How TPS keeps money: customer accounts, the double-entry ledger behind them, and the
rules that keep them consistent. The reasoning behind these choices is in
[ADR 0006](adr/0006-double-entry-ledger.md).

## In one picture

```
 accounts module                          ledger module
┌─────────────────────────┐        ┌──────────────────────────────────────────┐
│ AccountHolder           │        │ LedgerAccount   (balance, normal side)   │
│   └─ Account ───────────┼──1:1──▶│   ▲                                      │
│        number, status,  │        │   │ many                                 │
│        type, currency   │        │ Posting  (DEBIT/CREDIT, amount,          │
│   └─ status history     │        │   │       balance_after)                 │
└─────────────────────────┘        │   │ 2..n                                 │
                                   │ JournalEntry  (one financial event)      │
                                   └──────────────────────────────────────────┘
```

- An **account holder** is a person or a business, identified by one identity
  document.
- An **account** has a 12-digit number, a type (`SAVINGS`, `CHECKING`), one
  currency (`HNL` or `USD`) and a status. Its balance and movements are **not**
  stored in the account: they belong to its **ledger account**.
- The **ledger** records every movement as a **journal entry** of balanced
  **postings**.

## Double-entry bookkeeping

Every movement of money is a journal entry with at least two postings, all in one
currency, whose **debits equal their credits**. Money is therefore never created
or lost: it always comes from somewhere and goes somewhere.

Whether a debit or a credit *increases* a balance depends on the account type (its
*normal side*):

| Type | Increased by | In TPS |
|---|---|---|
| `ASSET` | debit | Settlement accounts: money TPS holds at its bank (`SETTLEMENT-HNL`, `SETTLEMENT-USD`) |
| `LIABILITY` | credit | Customer accounts: money TPS owes each customer (`CUS-<account number>`) |
| `EQUITY`, `REVENUE` | credit | Not used yet (capital, fees) |
| `EXPENSE` | debit | Not used yet (provider costs) |

### Example: a deposit and a withdrawal

A customer deposits 1,000.00 HNL in cash, then withdraws 250.50 HNL:

| Entry | Account | Debit | Credit | Balance after |
|---|---|---:|---:|---:|
| Cash deposit | `SETTLEMENT-HNL` (asset) | 1000.00 | | 1000.00 |
| | `CUS-100000000093` (liability) | | 1000.00 | 1000.00 |
| Cash withdrawal | `CUS-100000000093` | 250.50 | | 749.50 |
| | `SETTLEMENT-HNL` | | 250.50 | 749.50 |

Each entry is balanced (1000.00 = 1000.00, 250.50 = 250.50). On the customer's
statement a **credit is money in** and a **debit is money out**, as on any bank
statement.

Deposits and withdrawals are made through the transaction engine
([transactions.md](transactions.md)), which posts these entries; transfers arrive in
Phase 5. No endpoint posts to the ledger directly.

## Rules and where they are enforced

Every rule is checked in Java first, so the API answers with a clear error, and
again by PostgreSQL, so it also holds for SQL that bypasses the application.

| Rule | Java | PostgreSQL | API error |
|---|---|---|---|
| An entry has 2+ postings, one currency, positive amounts | `JournalEntry.post` | deferred trigger at `COMMIT`, `CHECK` | `422 INVALID_JOURNAL_ENTRY` |
| Debits equal credits | `JournalEntry.post` | deferred trigger at `COMMIT` | `422 UNBALANCED_JOURNAL_ENTRY` |
| A posting uses its account's currency | `JournalEntry.post` | composite foreign key | `422 CURRENCY_MISMATCH` |
| A customer account never goes negative | `JournalEntry.post` | `CHECK (allow_negative OR balance >= 0)` | `422 INSUFFICIENT_FUNDS` |
| A closed account accepts no postings | `JournalEntry.post` | — | `422 LEDGER_ACCOUNT_CLOSED` |
| Only an empty account can be closed | `AccountService`, `LedgerAccount` | `CHECK` | `422 ACCOUNT_HAS_BALANCE` |
| Entries, postings and status history never change | `@Immutable`, no update methods | triggers reject `UPDATE`/`DELETE`/`TRUNCATE` | — |
| Accounts, holders and ledger accounts are never deleted | no delete methods | triggers reject `DELETE`/`TRUNCATE` | — |
| Account numbers have valid check digits | `AccountNumber` | `CHECK (number % 97 = 1)` | — |
| Documents follow their format | `DocumentType`, request validation | `CHECK` | `400 VALIDATION_FAILED` |

A rejected entry leaves no trace: every rule is checked before any balance
changes, and the whole operation runs in one database transaction.

## Concurrency

Posting an entry locks its ledger accounts (`SELECT ... FOR UPDATE`) **in
ascending id order** until the transaction commits:

- Two entries on the same account are applied one after the other, each starting
  from the balance left by the previous one, so no update is lost and every
  `balance_after` is exact.
- Two entries that touch the same two accounts in opposite directions lock them in
  the same order, so they wait for each other instead of deadlocking.
- Racing debits cannot overdraw an account: each one sees the balance left by the
  previous one.

`LedgerConcurrencyTests` runs these three scenarios with 8 threads against a real
PostgreSQL database.

## Customer accounts

### Holders and identity documents

| `documentType` | Format | Holder types |
|---|---|---|
| `DNI` | 13 digits, dashes allowed: `0801-1990-12345` | `PERSON` |
| `RTN` | 14 digits, dashes allowed | `PERSON`, `BUSINESS` |
| `PASSPORT` | 6 to 20 letters or digits | `PERSON` |

Numbers are stored normalized (no spaces or dashes, uppercase), so the same
document cannot be registered twice in different formats. Document numbers are
personal data: they are never logged nor repeated in error messages.

### Account numbers

12 digits: a 10-digit sequence plus 2 check digits (ISO 7064 MOD 97-10, the IBAN
algorithm). The whole number divided by 97 leaves remainder 1; any single
mistyped digit or swap of two neighbouring digits breaks that.

```
sequence 1000000000 → check digits 98 − (100000000000 mod 97) = 93 → 100000000093
```

### Status

```
ACTIVE ⇄ SUSPENDED
   ↘        ↙
     CLOSED        final, only with a zero balance
```

Every change requires a `reason` and is stored in the status history (who, when,
from, to, why). The opening is the first entry. Closing an account also closes its
ledger account. Only an `ACTIVE` account sends or receives money: a `SUSPENDED` or
`CLOSED` account is refused by the transaction engine (`ACCOUNT_SUSPENDED`,
`ACCOUNT_CLOSED`; see [transactions.md](transactions.md#rules)).

### Balances

`GET /api/v1/accounts/{id}/balance` returns:

- `ledgerBalance`: the sum of all posted movements.
- `availableBalance`: what the holder can use, the ledger balance minus funds on
  hold. Holds arrive with pending payments (Phase 6); until then both values
  are equal.

## Endpoints

| Method and path | Permission | Description |
|---|---|---|
| `POST /api/v1/account-holders` | `ACCOUNT_MANAGE` | Register a holder |
| `GET /api/v1/account-holders?documentNumber=` | `ACCOUNT_READ` | List holders (by name), optionally by document |
| `GET /api/v1/account-holders/{holderId}` | `ACCOUNT_READ` | Get a holder |
| `POST /api/v1/accounts` | `ACCOUNT_MANAGE` | Open an account |
| `GET /api/v1/accounts?holderId=&status=&accountNumber=` | `ACCOUNT_READ` | List accounts (by number) |
| `GET /api/v1/accounts/{accountId}` | `ACCOUNT_READ` | Get an account |
| `PUT /api/v1/accounts/{accountId}/status` | `ACCOUNT_MANAGE` | Suspend, reactivate or close |
| `GET /api/v1/accounts/{accountId}/balance` | `ACCOUNT_READ` | Ledger and available balance |
| `GET /api/v1/accounts/{accountId}/entries` | `ACCOUNT_READ` | Movements, newest first |
| `GET /api/v1/accounts/{accountId}/status-history` | `ACCOUNT_READ` | Status history, newest first |
| `GET /api/v1/ledger/accounts` | `LEDGER_READ` | Ledger accounts (by code) |
| `GET /api/v1/ledger/accounts/{ledgerAccountId}` | `LEDGER_READ` | Get a ledger account |
| `GET /api/v1/ledger/accounts/{ledgerAccountId}/postings` | `LEDGER_READ` | Postings, newest first |
| `GET /api/v1/ledger/journal-entries/{journalEntryId}` | `LEDGER_READ` | A journal entry with its lines |
| `GET /api/v1/ledger/verification` | `LEDGER_READ` | Verify the whole ledger |

### Example: open an account (as an operator)

```bash
curl -X POST http://localhost:8080/api/v1/account-holders \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"holderType": "PERSON", "fullName": "María López",
       "documentType": "DNI", "documentNumber": "0801-1990-12345"}'

curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"holderId": "<id from the previous response>", "type": "SAVINGS", "currency": "HNL"}'
```

```json
{
  "id": "0193b2c4-...",
  "accountNumber": "100000000093",
  "holderId": "0193b2c3-...",
  "type": "SAVINGS",
  "currency": "HNL",
  "status": "ACTIVE",
  "ledgerAccountId": "0193b2c4-...",
  "openedAt": "2026-11-25T15:04:12.345Z",
  "updatedAt": "2026-11-25T15:04:12.345Z",
  "closedAt": null
}
```

### Example: verify the ledger (as an auditor)

```json
{
  "verifiedAt": "2026-11-25T15:10:00Z",
  "consistent": true,
  "journalEntriesChecked": 1250,
  "accountsChecked": 42,
  "unbalancedJournalEntries": [],
  "accountMismatches": [],
  "totals": [
    { "currency": "HNL", "totalDebits": "98500.00", "totalCredits": "98500.00", "balanced": true }
  ]
}
```

## Known limitations

- **No holds.** The available balance equals the ledger balance until pending
  payments exist (Phase 6).
- **Holders cannot be edited** (name or document) yet.
- **The verification scans the whole ledger.** Fine at this project's volumes; a
  production ledger would verify per period or incrementally.
- **Settlement accounts are serialization points.** Every deposit locks the
  settlement account of its currency. High-volume systems split such accounts.
