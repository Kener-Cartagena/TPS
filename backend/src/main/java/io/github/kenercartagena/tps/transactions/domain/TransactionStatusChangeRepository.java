package io.github.kenercartagena.tps.transactions.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Append-only: history entries are saved and read, never changed or deleted. */
public interface TransactionStatusChangeRepository extends Repository<TransactionStatusChange, UUID> {

    TransactionStatusChange save(TransactionStatusChange change);

    /** The history of a transaction, oldest first. */
    List<TransactionStatusChange> findByTransactionIdOrderBySequenceNumber(UUID transactionId);
}
