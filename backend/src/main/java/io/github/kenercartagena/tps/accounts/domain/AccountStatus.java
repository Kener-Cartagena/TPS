package io.github.kenercartagena.tps.accounts.domain;

/**
 * Status of an account and its allowed transitions:
 *
 * <pre>
 *   ACTIVE ⇄ SUSPENDED
 *      ↘       ↙
 *       CLOSED      (final; only with a zero balance)
 * </pre>
 *
 * <p>What each status allows (receiving or sending money) is enforced by the transaction
 * engine in Phase 4.
 */
public enum AccountStatus {

    /** Operates normally. */
    ACTIVE,
    /** Temporarily blocked, e.g. during an investigation; can be reactivated. */
    SUSPENDED,
    /** Permanently closed. */
    CLOSED;

    /** Whether an account in this status may change to the target status. */
    public boolean canChangeTo(AccountStatus target) {
        return switch (this) {
            case ACTIVE -> target == SUSPENDED || target == CLOSED;
            case SUSPENDED -> target == ACTIVE || target == CLOSED;
            case CLOSED -> false;
        };
    }
}
