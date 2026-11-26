/**
 * Transactions module: the transaction engine of TPS.
 *
 * <p>Every financial operation requested through the API (a deposit, a withdrawal; later
 * transfers, reversals and payments) becomes a <em>transaction</em> with a controlled
 * lifecycle. The engine checks the accounts and the funds, posts the journal entry to the
 * ledger, and records the outcome, all in one database transaction. Retries are safe:
 * every request carries an idempotency key, and a repeated key returns the result of the
 * first request instead of moving money again. See {@code docs/transactions.md} and
 * ADR 0007.
 *
 * <ul>
 *   <li>{@code transactions.domain} (internal) — the {@code Transaction} aggregate, its
 *       statuses and their allowed transitions, idempotency keys, persistence.</li>
 *   <li>{@code transactions.application} (internal) — the engine: idempotency, locking,
 *       validation, posting; one handler per transaction type; read models.</li>
 *   <li>{@code transactions.web} (internal) — REST controllers and request bodies.</li>
 * </ul>
 *
 * <p>The module sits on top of {@code accounts} (status and currency of each account) and
 * {@code ledger} (balances and postings); neither of them knows it exists.
 */
@ApplicationModule(displayName = "Transactions",
        allowedDependencies = {"accounts", "ledger", "identity",
                "shared :: error", "shared :: money", "shared :: web"})
package io.github.kenercartagena.tps.transactions;

import org.springframework.modulith.ApplicationModule;
