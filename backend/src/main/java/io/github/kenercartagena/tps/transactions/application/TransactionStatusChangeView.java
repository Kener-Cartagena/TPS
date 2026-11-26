package io.github.kenercartagena.tps.transactions.application;

import io.github.kenercartagena.tps.transactions.domain.TransactionStatus;
import io.github.kenercartagena.tps.transactions.domain.TransactionStatusChange;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One step of a transaction's status history.
 *
 * @param fromStatus {@code null} for the creation
 * @param reason     error code of the rule, for a rejection
 * @param changedBy  user whose request caused the change
 */
public record TransactionStatusChangeView(
        @Nullable TransactionStatus fromStatus,
        TransactionStatus toStatus,
        @Nullable String reason,
        @Nullable UUID changedBy,
        Instant changedAt) {

    static TransactionStatusChangeView of(TransactionStatusChange change) {
        return new TransactionStatusChangeView(change.getFromStatus(), change.getToStatus(),
                change.getReason(), change.getChangedBy(), change.getChangedAt());
    }
}
