package io.github.kenercartagena.tps.transactions.domain;

/**
 * Status of a transaction and the transitions between statuses:
 *
 * <pre>
 *   CREATED ──→ PROCESSING ──→ COMPLETED ──→ REVERSED
 *     │  │          │  └─────→ REJECTED
 *     │  │          └────────→ FAILED
 *     │  └─→ PENDING ──→ PROCESSING, REJECTED, FAILED
 *     └────→ REJECTED
 * </pre>
 *
 * <p>Which of them a transaction actually goes through depends on its type
 * ({@link TransactionType#uses}): deposits and withdrawals are processed at once and never
 * wait, so they never are {@code PENDING}.
 *
 * <p>{@code REJECTED} and {@code FAILED} both mean "no money moved", for different
 * reasons: a rejection is a business rule that refused the operation (insufficient funds,
 * a suspended account), always with the same answer for the same request; a failure is a
 * technical problem while processing (e.g. a payment provider that does not answer).
 */
public enum TransactionStatus {

    /** Recorded and accepted for processing. */
    CREATED,
    /** Waiting for something outside TPS, e.g. a payment provider (Phase 6). */
    PENDING,
    /** Being executed: accounts locked, rules being checked. */
    PROCESSING,
    /** Executed: the money moved and its journal entry is posted. */
    COMPLETED,
    /** Could not be processed for a technical reason; no money moved (Phase 6). */
    FAILED,
    /** Refused by a business rule; no money moved. */
    REJECTED,
    /** Undone by a reversal, which moved the money back (Phase 5). */
    REVERSED;

    /** Whether a transaction in this status may change to the target status. */
    public boolean canChangeTo(TransactionStatus target) {
        return switch (this) {
            case CREATED -> target == PENDING || target == PROCESSING || target == REJECTED;
            case PENDING -> target == PROCESSING || target == REJECTED || target == FAILED;
            case PROCESSING -> target == COMPLETED || target == REJECTED || target == FAILED;
            case COMPLETED -> target == REVERSED;
            case FAILED, REJECTED, REVERSED -> false;
        };
    }

    /** Whether the transaction has reached its outcome: it is no longer being processed. */
    public boolean isProcessed() {
        return this == COMPLETED || this == FAILED || this == REJECTED || this == REVERSED;
    }
}
