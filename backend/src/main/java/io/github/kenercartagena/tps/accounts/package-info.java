/**
 * Accounts module: account holders and their customer accounts.
 *
 * <p>An account has a number, a type, a currency and a status; its balance and movements
 * are kept by its own ledger account (a {@code LIABILITY}: money TPS owes the holder),
 * opened through the {@link io.github.kenercartagena.tps.ledger.Ledger} facade together
 * with the account.
 *
 * <ul>
 *   <li>{@code accounts.domain} (internal) — holders, accounts, account numbers, status
 *       rules and history, persistence.</li>
 *   <li>{@code accounts.application} (internal) — use cases and read models.</li>
 *   <li>{@code accounts.web} (internal) — REST controllers and request bodies.</li>
 * </ul>
 *
 * <p>The module has no public API yet: the transaction engine (Phase 4) adds the one it
 * needs to check an account's status before moving money.
 */
@ApplicationModule(displayName = "Accounts",
        allowedDependencies = {"ledger", "identity", "shared :: error", "shared :: money", "shared :: web"})
package io.github.kenercartagena.tps.accounts;

import org.springframework.modulith.ApplicationModule;
