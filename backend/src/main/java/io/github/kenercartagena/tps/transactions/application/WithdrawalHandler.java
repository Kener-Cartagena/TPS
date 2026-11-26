package io.github.kenercartagena.tps.transactions.application;

import io.github.kenercartagena.tps.accounts.OperableAccount;
import io.github.kenercartagena.tps.ledger.EntryDirection;
import io.github.kenercartagena.tps.ledger.SystemLedgerAccounts;
import io.github.kenercartagena.tps.transactions.application.MoneyMovement.Leg;
import io.github.kenercartagena.tps.transactions.domain.Transaction;
import io.github.kenercartagena.tps.transactions.domain.TransactionType;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Cash withdrawn at a branch: the reverse of a deposit. What TPS owes the customer shrinks
 * (debit the customer account) and so does the money it holds (credit the settlement
 * account).
 */
@Component
class WithdrawalHandler implements OperationHandler {

    @Override
    public TransactionType type() {
        return TransactionType.WITHDRAWAL;
    }

    @Override
    public MoneyMovement movementOf(Transaction transaction, Map<UUID, OperableAccount> accounts) {
        OperableAccount source = accounts.get(Objects.requireNonNull(transaction.getSourceAccountId()));
        return new MoneyMovement(OperationHandler.describe("Cash withdrawal", transaction), List.of(
                Leg.customer(source, EntryDirection.DEBIT),
                Leg.internal(SystemLedgerAccounts.settlement(transaction.getCurrency()), EntryDirection.CREDIT)));
    }
}
