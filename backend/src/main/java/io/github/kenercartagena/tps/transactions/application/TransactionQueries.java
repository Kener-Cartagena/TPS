package io.github.kenercartagena.tps.transactions.application;

import io.github.kenercartagena.tps.shared.error.ResourceNotFoundException;
import io.github.kenercartagena.tps.transactions.domain.Transaction;
import io.github.kenercartagena.tps.transactions.domain.TransactionRepository;
import io.github.kenercartagena.tps.transactions.domain.TransactionStatusChangeRepository;
import io.github.kenercartagena.tps.transactions.domain.TransactionsErrorCode;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only use cases: transactions and their history. */
@Service
@Transactional(readOnly = true)
public class TransactionQueries {

    private final TransactionRepository transactions;
    private final TransactionStatusChangeRepository history;

    TransactionQueries(TransactionRepository transactions, TransactionStatusChangeRepository history) {
        this.transactions = transactions;
        this.history = history;
    }

    public TransactionView get(UUID transactionId) {
        return TransactionView.of(find(transactionId));
    }

    /** One page of transactions matching the filter, newest first. */
    public Page<TransactionView> list(TransactionFilter filter, int page, int size) {
        PageRequest request = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return transactions.findAll(filter.toSpecification(), request).map(TransactionView::of);
    }

    /**
     * Every status of the transaction, oldest first: its lifecycle in order. Not paginated:
     * a transaction has only a handful of statuses.
     */
    public List<TransactionStatusChangeView> statusHistory(UUID transactionId) {
        if (!transactions.existsById(transactionId)) {
            throw notFound(transactionId);
        }
        return history.findByTransactionIdOrderBySequenceNumber(transactionId).stream()
                .map(TransactionStatusChangeView::of)
                .toList();
    }

    private Transaction find(UUID transactionId) {
        return transactions.findById(transactionId).orElseThrow(() -> notFound(transactionId));
    }

    private static ResourceNotFoundException notFound(UUID transactionId) {
        return new ResourceNotFoundException(TransactionsErrorCode.TRANSACTION_NOT_FOUND,
                "Transaction '%s' was not found.".formatted(transactionId));
    }
}
