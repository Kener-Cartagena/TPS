package io.github.kenercartagena.tps.transactions.application;

import io.github.kenercartagena.tps.shared.error.BusinessRuleViolationException;
import io.github.kenercartagena.tps.transactions.domain.IdempotencyKey;
import io.github.kenercartagena.tps.transactions.domain.RequestFingerprint;
import io.github.kenercartagena.tps.transactions.domain.Transaction;
import io.github.kenercartagena.tps.transactions.domain.TransactionRepository;
import io.github.kenercartagena.tps.transactions.domain.TransactionsErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Entry point of the transaction engine: makes every operation idempotent.
 *
 * <p>Each request carries an idempotency key, unique per user. The first request with a
 * key is processed ({@link TransactionProcessor}); any later request with the same key
 * gets the result of the first one, and nothing is processed again:
 *
 * <ul>
 *   <li>same key, same request: the stored transaction, marked as replayed;</li>
 *   <li>same key, different request: {@code 422 IDEMPOTENCY_KEY_REUSED};</li>
 *   <li>same key, at the same time: the database makes the second request wait on the
 *       unique key until the first one commits; its insert then fails, and it returns the
 *       transaction of the first one.</li>
 * </ul>
 *
 * <p>Deliberately <strong>not</strong> {@code @Transactional}: the processing runs in its
 * own database transaction, and when a duplicate key aborts it, this class must still be
 * able to read the winning transaction in a new one.
 */
@Service
public class TransactionEngine {

    private static final Logger log = LoggerFactory.getLogger(TransactionEngine.class);

    private final TransactionProcessor processor;
    private final TransactionRepository transactions;

    TransactionEngine(TransactionProcessor processor, TransactionRepository transactions) {
        this.processor = processor;
        this.transactions = transactions;
    }

    /**
     * Processes the request once per idempotency key.
     *
     * @param actorId the user making the request; keys are unique per user
     * @throws BusinessRuleViolationException {@code IDEMPOTENCY_KEY_REUSED} if the key was
     *         used for a different request
     */
    public TransactionOutcome submit(TransactionRequest request, IdempotencyKey key, UUID actorId) {
        RequestFingerprint fingerprint = request.fingerprint();
        Optional<Transaction> previous = findByKey(actorId, key);
        if (previous.isPresent()) {
            return replay(previous.get(), fingerprint);
        }
        try {
            return new TransactionOutcome(TransactionView.of(processor.process(request, key, actorId)), false);
        } catch (DataIntegrityViolationException ex) {
            // Most likely a concurrent request with the same key committed first. If so,
            // answer with its result; otherwise it is another integrity problem: rethrow.
            return findByKey(actorId, key)
                    .map(winner -> replay(winner, fingerprint))
                    .orElseThrow(() -> ex);
        }
    }

    private TransactionOutcome replay(Transaction previous, RequestFingerprint fingerprint) {
        if (!previous.getRequestFingerprint().equals(fingerprint)) {
            throw new BusinessRuleViolationException(TransactionsErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "This Idempotency-Key was already used for a different request. "
                            + "Use a new key for every new operation.");
        }
        log.info("Retried request answered with transaction {} ({})", previous.getId(),
                previous.getStatus());
        return new TransactionOutcome(TransactionView.of(previous), true);
    }

    private Optional<Transaction> findByKey(UUID actorId, IdempotencyKey key) {
        return transactions.findByCreatedByAndIdempotencyKey(actorId, key.value());
    }
}
