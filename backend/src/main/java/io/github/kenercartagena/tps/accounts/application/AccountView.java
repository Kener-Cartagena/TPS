package io.github.kenercartagena.tps.accounts.application;

import io.github.kenercartagena.tps.accounts.domain.Account;
import io.github.kenercartagena.tps.accounts.AccountStatus;
import io.github.kenercartagena.tps.accounts.domain.AccountType;
import io.github.kenercartagena.tps.shared.money.Currency;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Read model of an account, returned by the API. The balance has its own endpoint.
 *
 * @param ledgerAccountId the ledger account that holds the balance and movements; auditors
 *                        can read it under {@code /api/v1/ledger/accounts/{id}}
 */
public record AccountView(
        UUID id,
        String accountNumber,
        UUID holderId,
        AccountType type,
        Currency currency,
        AccountStatus status,
        UUID ledgerAccountId,
        Instant openedAt,
        Instant updatedAt,
        @Nullable Instant closedAt) {

    static AccountView of(Account account) {
        return new AccountView(account.getId(), account.getAccountNumber().value(),
                account.getHolderId(), account.getType(), account.getCurrency(),
                account.getStatus(), account.getLedgerAccountId(), account.getOpenedAt(),
                account.getUpdatedAt(), account.getClosedAt());
    }
}
