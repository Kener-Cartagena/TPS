package io.github.kenercartagena.tps.transactions.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TransactionRepository
        extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    /** The transaction a user created with this idempotency key, if any. */
    Optional<Transaction> findByCreatedByAndIdempotencyKey(UUID createdBy, String idempotencyKey);
}
