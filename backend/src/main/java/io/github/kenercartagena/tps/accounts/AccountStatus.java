package io.github.kenercartagena.tps.accounts;

/**
 * Status of an account, its allowed transitions, and the money movements it allows.
 * Part of the public API of the accounts module.
 *
 * <pre>
 *   ACTIVE ⇄ SUSPENDED
 *      ↘       ↙
 *       CLOSED      (final; only with a zero balance)
 * </pre>
 *
 * <table>
 *   <caption>Money movements allowed per status</caption>
 *   <tr><th>Status</th><th>Debit (money out)</th><th>Credit (money in)</th></tr>
 *   <tr><td>ACTIVE</td><td>yes</td><td>yes</td></tr>
 *   <tr><td>SUSPENDED</td><td>no</td><td>no</td></tr>
 *   <tr><td>CLOSED</td><td>no</td><td>no</td></tr>
 * </table>
 *
 * <p>A suspended account is frozen in both directions: while it is under investigation no
 * money enters or leaves it (ADR 0007).
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

    /** Whether money may leave an account in this status. */
    public boolean allowsDebits() {
        return this == ACTIVE;
    }

    /** Whether money may enter an account in this status. */
    public boolean allowsCredits() {
        return this == ACTIVE;
    }
}
