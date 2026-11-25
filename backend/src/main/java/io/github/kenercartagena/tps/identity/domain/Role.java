package io.github.kenercartagena.tps.identity.domain;

import io.github.kenercartagena.tps.identity.Permissions;
import java.util.Set;

/**
 * Role of a user. Each user has exactly one role, and each role grants a fixed set of
 * {@link Permissions}.
 *
 * <p>Roles follow <strong>segregation of duties</strong>: the administrator manages who
 * can access the system but does not move money; operators execute financial
 * operations; auditors can read but never change anything. Permissions for accounts,
 * transfers, payments, ... are added in the phases that introduce those operations
 * (the planned matrix is in {@code docs/security.md}).
 */
public enum Role {

    /** Manages users and their access. Does not execute financial operations. */
    SYSTEM_ADMIN(Set.of(Permissions.USER_READ, Permissions.USER_MANAGE)),

    /** Executes day-to-day financial operations. */
    OPERATOR(Set.of()),

    /** Read-only access for audit and compliance, including who has access. */
    AUDITOR(Set.of(Permissions.USER_READ));

    private final Set<String> permissions;

    Role(Set<String> permissions) {
        this.permissions = permissions;
    }

    /** The permissions granted by this role (immutable). */
    public Set<String> permissions() {
        return permissions;
    }
}
