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
    void operatorManagesAccountsButNotUsersNorTheLedger() {
        assertThat(Role.OPERATOR.permissions())
                .containsExactlyInAnyOrder(Permissions.ACCOUNT_READ, Permissions.ACCOUNT_MANAGE);
    }

    @Test
    void auditorCanOnlyRead() {
        assertThat(Role.AUDITOR.permissions()).containsExactlyInAnyOrder(
                Permissions.USER_READ, Permissions.ACCOUNT_READ, Permissions.LEDGER_READ);
    }

    @Test
    void onlyTheAdministratorCanManageUsers() {
        for (Role role : Role.values()) {
            assertThat(role.permissions().contains(Permissions.USER_MANAGE))
                    .as("%s can manage users", role)
                    .isEqualTo(role == Role.SYSTEM_ADMIN);
        }
    }

    /** Segregation of duties: whoever manages access never manages money, and vice versa. */
    @Test
    void noRoleManagesBothUsersAndAccounts() {
        for (Role role : Role.values()) {
            assertThat(role.permissions().contains(Permissions.USER_MANAGE)
                    && role.permissions().contains(Permissions.ACCOUNT_MANAGE))
                    .as("%s manages users and accounts", role)
                    .isFalse();
        }
    }

    /** The ledger is the auditor's view of the books; operators work through accounts. */
    @Test
    void onlyTheAuditorReadsTheLedger() {
        for (Role role : Role.values()) {
            assertThat(role.permissions().contains(Permissions.LEDGER_READ))
                    .as("%s reads the ledger", role)
                    .isEqualTo(role == Role.AUDITOR);
        }
    }
}
