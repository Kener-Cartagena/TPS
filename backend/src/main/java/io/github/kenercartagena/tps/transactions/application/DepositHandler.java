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
 * Cash deposited at a branch. The money TPS holds grows (debit the settlement account, an
 * asset) and so does what it owes the customer (credit the customer account, a liability).
 */
@Component
class DepositHandler implements OperationHandler {

    @Override
    public TransactionType type() {
        return TransactionType.DEPOSIT;
    }

    @Override
    public MoneyMovement movementOf(Transaction transaction, Map<UUID, OperableAccount> accounts) {
        OperableAccount destination = accounts.get(
                Objects.requireNonNull(transaction.getDestinationAccountId()));
        return new MoneyMovement(OperationHandler.describe("Cash deposit", transaction), List.of(
                Leg.internal(SystemLedgerAccounts.settlement(transaction.getCurrency()), EntryDirection.DEBIT),
                Leg.customer(destination, EntryDirection.CREDIT)));
    }
}
