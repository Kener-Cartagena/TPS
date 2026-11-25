/**
 * Ledger module: the general ledger of TPS, kept with double-entry bookkeeping.
 *
 * <p>Every movement of money is a <em>journal entry</em> made of two or more
 * <em>postings</em> (debits and credits) whose totals are equal, so money is never
 * created or lost. Entries are immutable: a mistake is corrected with a new entry, never
 * by editing an old one. See {@code docs/ledger.md} and ADR 0006.
 *
 * <ul>
 *   <li>{@code ledger} (public) — {@link io.github.kenercartagena.tps.ledger.Ledger}, the
 *       facade other modules use to open ledger accounts, post entries and read balances,
 *       and the types of its contract.</li>
 *   <li>{@code ledger.domain} (internal) — ledger accounts, journal entries and postings,
 *       the posting rules, persistence.</li>
 *   <li>{@code ledger.application} (internal) — the facade implementation, read models
 *       and the ledger verification.</li>
 *   <li>{@code ledger.web} (internal) — read-only REST API for auditors.</li>
 * </ul>
 *
 * <p>The ledger is the lowest business module: it knows nothing about customers,
 * accounts or transactions. Its allowed dependencies are declared below, so Spring
 * Modulith fails the build if it ever starts depending on a module built on top of it.
 */
@ApplicationModule(displayName = "Ledger",
        allowedDependencies = {"shared :: error", "shared :: money", "shared :: web", "identity"})
package io.github.kenercartagena.tps.ledger;

import org.springframework.modulith.ApplicationModule;
