package io.github.kenercartagena.tps.transactions.web;

import io.github.kenercartagena.tps.shared.money.Currency;
import io.github.kenercartagena.tps.shared.money.Money;
import io.github.kenercartagena.tps.transactions.application.TransactionRequest;
import io.github.kenercartagena.tps.transactions.domain.Transaction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Body of a deposit or a withdrawal.
 *
 * @param amount   positive decimal string with at most 2 decimal places
 * @param currency must be the currency of the account
 */
record CashOperationRequest(
        @NotNull UUID accountId,
        @NotNull
        @Pattern(regexp = AMOUNT_FORMAT, message = "must be a decimal number with at most 2 decimal places, e.g. 1250.50")
        @DecimalMin(value = "0.01", message = "must be greater than 0")
        @Schema(example = "1250.50", type = "string")
        String amount,
        @NotNull Currency currency,
        @Nullable
        @Size(max = Transaction.MAX_DESCRIPTION_LENGTH)
        @Pattern(regexp = NOT_BLANK, message = "must not be blank")
        String description) {

    /** Up to 17 integer digits (the {@code NUMERIC(19,2)} columns) and 2 decimals. */
    static final String AMOUNT_FORMAT = "^[0-9]{1,17}(\\.[0-9]{1,2})?$";

    /** At least one character that is not a space. */
    static final String NOT_BLANK = "(?s).*\\S.*";

    TransactionRequest toDeposit() {
        return TransactionRequest.deposit(accountId, money(), description);
    }

    TransactionRequest toWithdrawal() {
        return TransactionRequest.withdrawal(accountId, money(), description);
    }

    private Money money() {
        return Money.of(amount, currency);
    }
}
