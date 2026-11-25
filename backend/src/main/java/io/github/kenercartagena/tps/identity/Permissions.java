package io.github.kenercartagena.tps.identity;

/**
 * Permissions of the TPS authorization model: the public API of the identity module.
 *
 * <p>Each role is granted a fixed set of permissions (see {@code docs/security.md}), and
 * every protected operation checks a permission, never a role:
 *
 * <pre>{@code
 * @PreAuthorize("hasAuthority('" + Permissions.USER_MANAGE + "')")
 * }</pre>
 *
 * <p>Checking permissions keeps operations independent of how roles are defined: a
 * role can gain or lose a permission without touching the protected code.
 *
 * <p>A permission is added in the phase that introduces the operation it protects.
 * Values are part of the authorization contract: never rename them.
 */
public final class Permissions {

    /** View users and their roles. */
    public static final String USER_READ = "USER_READ";

    /** Create users and change their role or status. */
    public static final String USER_MANAGE = "USER_MANAGE";

    /** View account holders, accounts, balances, movements and status history. */
    public static final String ACCOUNT_READ = "ACCOUNT_READ";

    /** Register account holders, open accounts and change their status. */
    public static final String ACCOUNT_MANAGE = "ACCOUNT_MANAGE";

    /** View the general ledger: internal accounts, journal entries and its verification. */
    public static final String LEDGER_READ = "LEDGER_READ";

    private Permissions() {
    }
}
