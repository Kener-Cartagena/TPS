package io.github.kenercartagena.tps.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kenercartagena.tps.identity.Permissions;
import org.junit.jupiter.api.Test;

/**
 * The role-to-permission matrix is a security decision (docs/security.md): any change to
 * it must be deliberate, so every role's permissions are pinned exactly.
 */
class RoleTests {

    @Test
    void systemAdminManagesUsersButHasNoFinancialPermissions() {
        assertThat(Role.SYSTEM_ADMIN.permissions())
                .containsExactlyInAnyOrder(Permissions.USER_READ, Permissions.USER_MANAGE);
    }

    @Test
    void operatorHasNoUserPermissions() {
        // Financial permissions (accounts, transfers, ...) arrive in later phases.
        assertThat(Role.OPERATOR.permissions()).isEmpty();
    }

    @Test
    void auditorCanOnlyRead() {
        assertThat(Role.AUDITOR.permissions()).containsExactly(Permissions.USER_READ);
    }

    @Test
    void onlyTheAdministratorCanManageUsers() {
        for (Role role : Role.values()) {
            assertThat(role.permissions().contains(Permissions.USER_MANAGE))
                    .as("%s can manage users", role)
                    .isEqualTo(role == Role.SYSTEM_ADMIN);
        }
    }
}
