package io.github.kenercartagena.tps.transactions.domain;

import static io.github.kenercartagena.tps.transactions.domain.TransactionStatus.COMPLETED;
import static io.github.kenercartagena.tps.transactions.domain.TransactionStatus.CREATED;
import static io.github.kenercartagena.tps.transactions.domain.TransactionStatus.PROCESSING;
import static io.github.kenercartagena.tps.transactions.domain.TransactionStatus.REJECTED;

import java.util.EnumSet;
import java.util.Set;

/**
 * Kind of financial operation. The type decides which accounts a transaction uses and
 * which statuses it goes through. Transfers and reversals arrive in Phase 5, payments in
 * Phase 6.
 */
public enum TransactionType {

    /** Cash deposited at a branch: money enters the destination account. */
    DEPOSIT(false, true, EnumSet.of(CREATED, PROCESSING, COMPLETED, REJECTED)),
    /** Cash withdrawn at a branch: money leaves the source account. */
    WITHDRAWAL(true, false, EnumSet.of(CREATED, PROCESSING, COMPLETED, REJECTED));

    private final boolean hasSource;
    private final boolean hasDestination;
    private final Set<TransactionStatus> statuses;

    TransactionType(boolean hasSource, boolean hasDestination, Set<TransactionStatus> statuses) {
        this.hasSource = hasSource;
        this.hasDestination = hasDestination;
        this.statuses = statuses;
    }

    /** Whether money leaves an account (the source, debited). */
    public boolean hasSource() {
        return hasSource;
    }

    /** Whether money enters an account (the destination, credited). */
    public boolean hasDestination() {
        return hasDestination;
    }

    /** Whether a transaction of this type can ever be in the status. */
    public boolean uses(TransactionStatus status) {
        return statuses.contains(status);
    }
}
