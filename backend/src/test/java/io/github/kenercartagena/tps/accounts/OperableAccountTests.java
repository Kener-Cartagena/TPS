package io.github.kenercartagena.tps.accounts;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kenercartagena.tps.shared.error.BusinessRuleViolationException;
import io.github.kenercartagena.tps.shared.money.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The status rules as the transaction engine sees them, with their error codes. */
class OperableAccountTests {

    @Test
    void anActiveAccountCanBeDebitedAndCredited() {
        OperableAccount account = accountIn(AccountStatus.ACTIVE);

        assertThatCode(account::requireDebitable).doesNotThrowAnyException();
        assertThatCode(account::requireCreditable).doesNotThrowAnyException();
    }

    @Test
    void aSuspendedAccountRejectsBothDirections() {
        OperableAccount account = accountIn(AccountStatus.SUSPENDED);

        assertRejected(account::requireDebitable, "ACCOUNT_SUSPENDED");
        assertRejected(account::requireCreditable, "ACCOUNT_SUSPENDED");
    }

    @Test
    void aClosedAccountRejectsBothDirections() {
        OperableAccount account = accountIn(AccountStatus.CLOSED);

        assertRejected(account::requireDebitable, "ACCOUNT_CLOSED");
        assertRejected(account::requireCreditable, "ACCOUNT_CLOSED");
    }

    private static void assertRejected(Runnable check, String code) {
        assertThatThrownBy(check::run)
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting(ex -> ((BusinessRuleViolationException) ex).getErrorCode().code())
                .isEqualTo(code);
    }

    private static OperableAccount accountIn(AccountStatus status) {
        return new OperableAccount(UUID.randomUUID(), "100000000093", Currency.HNL, status,
                UUID.randomUUID());
    }
}
