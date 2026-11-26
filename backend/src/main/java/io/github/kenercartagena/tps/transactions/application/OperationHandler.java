package io.github.kenercartagena.tps.transactions.application;

import io.github.kenercartagena.tps.accounts.OperableAccount;
import io.github.kenercartagena.tps.transactions.domain.Transaction;
import io.github.kenercartagena.tps.transactions.domain.TransactionType;
import java.util.Map;
import java.util.UUID;

/**
 * What is specific to one transaction type (Strategy pattern): how it moves money. The
 * engine ({@link TransactionProcessor}) does everything else the same way for every type:
 * idempotency, locking, the status and funds rules, posting, the lifecycle.
 *
 * <p>A new type (a transfer in Phase 5) is a new handler; the engine does not change.
 * Every {@link TransactionType} must have exactly one handler, or the application does
 * not start.
 */
interface OperationHandler {

    TransactionType type();

    /**
     * @param accounts the customer accounts of the transaction, locked, by id
     */
    MoneyMovement movementOf(Transaction transaction, Map<UUID, OperableAccount> accounts);

    /** The journal entry description: what happened, plus the client's note if any. */
    static String describe(String operation, Transaction transaction) {
        String note = transaction.getDescription();
        return note == null ? operation : operation + ": " + note;
    }
}
