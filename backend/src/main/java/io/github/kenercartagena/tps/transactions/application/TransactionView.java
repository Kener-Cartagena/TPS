package io.github.kenercartagena.tps.transactions.application;

import io.github.kenercartagena.tps.shared.money.Currency;
import io.github.kenercartagena.tps.transactions.domain.Transaction;
import io.github.kenercartagena.tps.transactions.domain.TransactionStatus;
import io.github.kenercartagena.tps.transactions.domain.TransactionType;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Read model of a transaction, returned by the API.
 *
 * @param amount               decimal string, e.g. {@code "1250.50"}
 * @param sourceAccountId      account debited (money out); {@code null} for deposits
 * @param destinationAccountId account credited (money in); {@code null} for withdrawals
 * @param journalEntryId       ledger entry that moved the money; only when completed
 * @param rejectionCode        error code of the rule that refused it; only when rejected
 * @param rejectionDetail      explanation of the rejection; only when rejected
 * @param createdBy            user who requested it
 * @param processedAt          when it reached its outcome
 */
public record TransactionView(
        UUID id,
        TransactionType type,
        TransactionStatus status,
        String amount,
        Currency currency,
        @Nullable UUID sourceAccountId,
        @Nullable UUID destinationAccountId,
        @Nullable String description,
        @Nullable UUID journalEntryId,
        @Nullable String rejectionCode,
        @Nullable String rejectionDetail,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        @Nullable Instant processedAt) {

    static TransactionView of(Transaction transaction) {
        return new TransactionView(transaction.getId(), transaction.getType(), transaction.getStatus(),
                transaction.getAmount().toPlainString(), transaction.getCurrency(),
                transaction.getSourceAccountId(), transaction.getDestinationAccountId(),
                transaction.getDescription(), transaction.getJournalEntryId(),
                transaction.getRejectionCode(), transaction.getRejectionDetail(),
                transaction.getCreatedBy(), transaction.getCreatedAt(), transaction.getUpdatedAt(),
                transaction.getProcessedAt());
    }
}
