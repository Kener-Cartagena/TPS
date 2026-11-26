/**
 * Accounts module: account holders and their customer accounts.
 *
 * <p>An account has a number, a type, a currency and a status; its balance and movements
 * are kept by its own ledger account (a {@code LIABILITY}: money TPS owes the holder),
 * opened through the {@link io.github.kenercartagena.tps.ledger.Ledger} facade together
 * with the account.
 *
 * <ul>
 *   <li>{@code accounts} (public) — {@link io.github.kenercartagena.tps.accounts.Accounts},
 *       the facade the transaction engine uses to lock accounts and check their status
 *       before moving money; {@link io.github.kenercartagena.tps.accounts.OperableAccount}
 *       and {@link io.github.kenercartagena.tps.accounts.AccountStatus}, with the movements
 *       each status allows.</li>
 *   <li>{@code accounts.domain} (internal) — holders, accounts, account numbers, status
 *       history, persistence.</li>
 *   <li>{@code accounts.application} (internal) — use cases, read models and the facade
 *       implementation.</li>
 *   <li>{@code accounts.web} (internal) — REST controllers and request bodies.</li>
 * </ul>
 */
@ApplicationModule(displayName = "Accounts",
        allowedDependencies = {"ledger", "identity", "shared :: error", "shared :: money", "shared :: web"})
package io.github.kenercartagena.tps.accounts;

import org.springframework.modulith.ApplicationModule;
