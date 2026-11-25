package io.github.kenercartagena.tps.ledger;

import io.github.kenercartagena.tps.shared.money.Currency;
import java.util.Objects;

/**
 * A ledger account to open.
 *
 * @param code                 unique, stable code: 3 to 50 uppercase letters, digits or
 *                             {@code -}, e.g. {@code CUS-100000000093}
 * @param name                 human-readable name (at most 100 characters)
 * @param allowNegativeBalance whether the balance may go below zero; {@code false} for
 *                             customer accounts
 */
public record NewLedgerAccount(
        String code,
        String name,
        LedgerAccountType type,
        Currency currency,
        boolean allowNegativeBalance) {

    public NewLedgerAccount {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(currency, "currency");
    }
}
