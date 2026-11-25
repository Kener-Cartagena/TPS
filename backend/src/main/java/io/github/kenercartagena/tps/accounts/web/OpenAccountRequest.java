package io.github.kenercartagena.tps.accounts.web;

import io.github.kenercartagena.tps.accounts.application.OpenAccountCommand;
import io.github.kenercartagena.tps.accounts.domain.AccountType;
import io.github.kenercartagena.tps.shared.money.Currency;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Body of {@code POST /api/v1/accounts}. */
record OpenAccountRequest(
        @NotNull UUID holderId,
        @NotNull AccountType type,
        @NotNull Currency currency) {

    OpenAccountCommand toCommand() {
        return new OpenAccountCommand(holderId, type, currency);
    }
}
