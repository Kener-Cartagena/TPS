package io.github.kenercartagena.tps.accounts.domain;

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
