package io.github.kenercartagena.tps.ledger.application;

import io.github.kenercartagena.tps.ledger.LedgerAccountType;
import io.github.kenercartagena.tps.ledger.domain.LedgerAccount;
import io.github.kenercartagena.tps.ledger.domain.LedgerAccountStatus;
import io.github.kenercartagena.tps.shared.money.Currency;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Read model of a ledger account, returned by the API.
 *
 * @param balance decimal string on the account's normal side, e.g. {@code "1250.50"}
 */
public record LedgerAccountView(
        UUID id,
        String code,
        String name,
        LedgerAccountType type,
        Currency currency,
        String balance,
        boolean allowNegativeBalance,
        LedgerAccountStatus status,
        Instant createdAt,
        @Nullable Instant closedAt) {

    static LedgerAccountView of(LedgerAccount account) {
        return new LedgerAccountView(account.getId(), account.getCode(), account.getName(),
                account.getType(), account.getCurrency(), account.getBalance().toPlainString(),
                account.isNegativeBalanceAllowed(), account.getStatus(), account.getCreatedAt(),
                account.getClosedAt());
    }
}
