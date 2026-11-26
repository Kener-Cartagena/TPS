package io.github.kenercartagena.tps.transactions.application;

import java.util.Objects;

/**
 * Result of submitting a transaction.
 *
 * @param transaction its current state: {@code COMPLETED} or {@code REJECTED}
 * @param replayed    {@code true} if the request was a retry: nothing was processed now,
 *                    this is the result of the first request with the same key
 */
public record TransactionOutcome(TransactionView transaction, boolean replayed) {

    public TransactionOutcome {
        Objects.requireNonNull(transaction, "transaction");
    }
}
