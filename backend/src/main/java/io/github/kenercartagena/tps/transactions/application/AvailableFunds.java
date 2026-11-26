package io.github.kenercartagena.tps.transactions.application;

import io.github.kenercartagena.tps.accounts.OperableAccount;
import io.github.kenercartagena.tps.ledger.LedgerBalance;
import io.github.kenercartagena.tps.shared.error.BusinessRuleViolationException;
import io.github.kenercartagena.tps.shared.money.Money;
import io.github.kenercartagena.tps.transactions.domain.TransactionsErrorCode;
import org.springframework.stereotype.Component;

/**
 * The one place that decides how much of an account's money can be used.
 *
 * <p>Today it is the whole ledger balance: every operation completes at once, so nothing
 * is ever on hold. Payments (Phase 6) can wait for a provider; their amount will be held,
 * and subtracted here, so a pending payment cannot be spent twice.
 */
@Component
class AvailableFunds {

    /** What the holder can use now. */
    Money of(OperableAccount account, LedgerBalance balance) {
        return balance.balance();
    }

    /**
     * @throws BusinessRuleViolationException {@code INSUFFICIENT_FUNDS} if the account
     *         cannot pay the amount
     */
    void require(OperableAccount account, LedgerBalance balance, Money amount) {
        if (of(account, balance).compareTo(amount) < 0) {
            throw new BusinessRuleViolationException(TransactionsErrorCode.INSUFFICIENT_FUNDS,
                    "The account does not have enough funds for this operation.");
        }
    }
}
