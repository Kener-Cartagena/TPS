package io.github.kenercartagena.tps.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The status transitions of an account, pinned exactly: ACTIVE ⇄ SUSPENDED → CLOSED. */
class AccountStatusTests {

    @Test
    void activeCanBeSuspendedOrClosed() {
        assertThat(targetsOf(AccountStatus.ACTIVE))
                .containsExactlyInAnyOrder(AccountStatus.SUSPENDED, AccountStatus.CLOSED);
    }

    @Test
    void suspendedCanBeReactivatedOrClosed() {
        assertThat(targetsOf(AccountStatus.SUSPENDED))
                .containsExactlyInAnyOrder(AccountStatus.ACTIVE, AccountStatus.CLOSED);
    }

    @Test
    void closedIsFinal() {
        assertThat(targetsOf(AccountStatus.CLOSED)).isEmpty();
    }

    @Test
    void noStatusChangesToItself() {
        for (AccountStatus status : AccountStatus.values()) {
            assertThat(status.canChangeTo(status)).as(status.name()).isFalse();
        }
    }

    /** ADR 0007: a suspended account is frozen in both directions, a closed one too. */
    @Test
    void onlyAnActiveAccountMovesMoney() {
        assertThat(AccountStatus.ACTIVE.allowsDebits()).isTrue();
        assertThat(AccountStatus.ACTIVE.allowsCredits()).isTrue();
        for (AccountStatus status : EnumSet.of(AccountStatus.SUSPENDED, AccountStatus.CLOSED)) {
            assertThat(status.allowsDebits()).as("%s allows debits", status).isFalse();
            assertThat(status.allowsCredits()).as("%s allows credits", status).isFalse();
        }
    }

    private static Set<AccountStatus> targetsOf(AccountStatus from) {
        Set<AccountStatus> targets = EnumSet.noneOf(AccountStatus.class);
        for (AccountStatus target : AccountStatus.values()) {
            if (from.canChangeTo(target)) {
                targets.add(target);
            }
        }
        return targets;
    }
}
