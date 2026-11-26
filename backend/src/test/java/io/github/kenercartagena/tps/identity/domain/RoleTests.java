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
    void operatorManagesAccountsAndMovesMoneyButNotUsersNorTheLedger() {
        assertThat(Role.OPERATOR.permissions()).containsExactlyInAnyOrder(
                Permissions.ACCOUNT_READ, Permissions.ACCOUNT_MANAGE, Permissions.TRANSACTION_READ,
                Permissions.DEPOSIT_EXECUTE, Permissions.WITHDRAWAL_EXECUTE);
    }

    @Test
    void auditorCanOnlyRead() {
        assertThat(Role.AUDITOR.permissions()).containsExactlyInAnyOrder(
                Permissions.USER_READ, Permissions.ACCOUNT_READ, Permissions.LEDGER_READ,
                Permissions.TRANSACTION_READ);
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

    /** Segregation of duties: whoever manages access never moves money. */
    @Test
    void noRoleManagesUsersAndMovesMoney() {
        for (Role role : Role.values()) {
            boolean movesMoney = role.permissions().contains(Permissions.DEPOSIT_EXECUTE)
                    || role.permissions().contains(Permissions.WITHDRAWAL_EXECUTE);
            assertThat(role.permissions().contains(Permissions.USER_MANAGE) && movesMoney)
                    .as("%s manages users and moves money", role)
                    .isFalse();
        }
    }

    /** Auditors read everything and change nothing. */
    @Test
    void theAuditorNeverMovesMoney() {
        assertThat(Role.AUDITOR.permissions())
                .doesNotContain(Permissions.DEPOSIT_EXECUTE, Permissions.WITHDRAWAL_EXECUTE);
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
