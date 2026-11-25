package io.github.kenercartagena.tps.accounts.application;

import io.github.kenercartagena.tps.accounts.domain.AccountType;
import io.github.kenercartagena.tps.shared.money.Currency;
import java.util.UUID;

/** Input of {@link AccountService#open}. */
public record OpenAccountCommand(UUID holderId, AccountType type, Currency currency) {
}
