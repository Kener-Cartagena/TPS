package io.github.kenercartagena.tps.accounts;

import io.github.kenercartagena.tps.accounts.domain.AccountsErrorCode;
import io.github.kenercartagena.tps.shared.error.BusinessRuleViolationException;
import io.github.kenercartagena.tps.shared.money.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * An account locked for a money movement (see {@link Accounts#lockForOperation}): what the
 * transaction engine needs to know, and the rules of its status.
 *
 * @param ledgerAccountId the ledger account that holds its balance
 */
public record OperableAccount(
        UUID id,
        String accountNumber,
        Currency currency,
        AccountStatus status,
        UUID ledgerAccountId) {

    public OperableAccount {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountNumber, "accountNumber");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(ledgerAccountId, "ledgerAccountId");
    }

    /**
     * @throws BusinessRuleViolationException {@code ACCOUNT_SUSPENDED} or
     *         {@code ACCOUNT_CLOSED} if money cannot leave the account
     */
    public void requireDebitable() {
        if (!status.allowsDebits()) {
            throw blocked();
        }
    }

    /**
     * @throws BusinessRuleViolationException {@code ACCOUNT_SUSPENDED} or
     *         {@code ACCOUNT_CLOSED} if money cannot enter the account
     */
    public void requireCreditable() {
        if (!status.allowsCredits()) {
            throw blocked();
        }
    }

    private BusinessRuleViolationException blocked() {
        return status == AccountStatus.CLOSED
                ? new BusinessRuleViolationException(AccountsErrorCode.ACCOUNT_CLOSED,
                        "The account is closed: no money can enter or leave it.")
                : new BusinessRuleViolationException(AccountsErrorCode.ACCOUNT_SUSPENDED,
                        "The account is suspended: no money can enter or leave it.");
    }
}
