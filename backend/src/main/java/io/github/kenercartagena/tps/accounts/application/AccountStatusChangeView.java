package io.github.kenercartagena.tps.accounts.application;

import io.github.kenercartagena.tps.accounts.domain.AccountStatus;
import io.github.kenercartagena.tps.accounts.domain.AccountStatusChange;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One entry of an account's status history.
 *
 * @param fromStatus {@code null} for the opening of the account
 * @param changedBy  user who made the change
 */
public record AccountStatusChangeView(
        @Nullable AccountStatus fromStatus,
        AccountStatus toStatus,
        String reason,
        @Nullable UUID changedBy,
        Instant changedAt) {

    static AccountStatusChangeView of(AccountStatusChange change) {
        return new AccountStatusChangeView(change.getFromStatus(), change.getToStatus(),
                change.getReason(), change.getChangedBy(), change.getChangedAt());
    }
}
