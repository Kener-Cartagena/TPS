package io.github.kenercartagena.tps.transactions.application;

import io.github.kenercartagena.tps.accounts.Accounts;
import io.github.kenercartagena.tps.accounts.OperableAccount;
import io.github.kenercartagena.tps.ledger.EntryDirection;
import io.github.kenercartagena.tps.ledger.Ledger;
import io.github.kenercartagena.tps.ledger.LedgerBalance;
import io.github.kenercartagena.tps.ledger.PostedJournalEntry;
import io.github.kenercartagena.tps.shared.error.BusinessRuleViolationException;
import io.github.kenercartagena.tps.transactions.application.MoneyMovement.Leg;
import io.github.kenercartagena.tps.transactions.domain.IdempotencyKey;
import io.github.kenercartagena.tps.transactions.domain.Transaction;
import io.github.kenercartagena.tps.transactions.domain.TransactionRepository;
import io.github.kenercartagena.tps.transactions.domain.TransactionStatusChange;
import io.github.kenercartagena.tps.transactions.domain.TransactionStatusChangeRepository;
import io.github.kenercartagena.tps.transactions.domain.TransactionType;
import io.github.kenercartagena.tps.transactions.domain.TransactionsErrorCode;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processes one transaction in <strong>one database transaction</strong>: it is recorded,
 * checked and executed, and its outcome stored, or nothing at all happens.
 *
 * <ol>
 *   <li>Limit lock waits ({@code lock_timeout}).</li>
 *   <li>Lock the customer accounts ({@code FOR SHARE}): their status cannot change now.</li>
 *   <li>Insert the transaction ({@code CREATED}). Its idempotency key is unique: a
 *       concurrent request with the same key waits here until this one ends.</li>
 *   <li>{@code PROCESSING}: lock the ledger accounts ({@code FOR UPDATE}) and check the
 *       rules with the balances that cannot change any more.</li>
 *   <li>A rule fails: {@code REJECTED} with its code, and the transaction commits
 *       normally, so the rejection is recorded and a retry gets the same answer.</li>
 *   <li>Every rule passes: post the journal entry, then {@code COMPLETED}.</li>
 * </ol>
 *
 * <p>Why rules are checked <em>before</em> posting, instead of letting the ledger reject
 * the entry: an exception that leaves a {@code @Transactional} method of another bean (like
 * {@code Ledger.post}) marks the whole transaction rollback-only, even if it is caught. The
 * rejection could not be committed. The rules are therefore checked in plain Java here; the
 * ledger's own checks stay as a second line of defense.
 *
 * <p>Any other failure (account not found, lock timeout, a bug) rolls everything back:
 * nothing is recorded, the idempotency key stays free, and retrying is safe.
 */
@Component
class TransactionProcessor {

    private static final Logger log = LoggerFactory.getLogger(TransactionProcessor.class);

    private final TransactionRepository transactions;
    private final TransactionStatusChangeRepository history;
    private final Accounts accounts;
    private final Ledger ledger;
    private final AvailableFunds availableFunds;
    private final LockTimeout lockTimeout;
    private final Map<TransactionType, OperationHandler> handlers;
    private final Clock clock;

    TransactionProcessor(TransactionRepository transactions, TransactionStatusChangeRepository history,
            Accounts accounts, Ledger ledger, AvailableFunds availableFunds, LockTimeout lockTimeout,
            List<OperationHandler> handlers, Clock clock) {
        this.transactions = transactions;
        this.history = history;
        this.accounts = accounts;
        this.ledger = ledger;
        this.availableFunds = availableFunds;
        this.lockTimeout = lockTimeout;
        this.handlers = byType(handlers);
        this.clock = clock;
    }

    @Transactional
    public Transaction process(TransactionRequest request, IdempotencyKey key, UUID actorId) {
        lockTimeout.applyToCurrentTransaction();
        Map<UUID, OperableAccount> locked = accounts.lockForOperation(request.accountIds());

        Transaction transaction = Transaction.create(request.type(), request.amount(),
                request.sourceAccountId(), request.destinationAccountId(), request.description(), key,
                request.fingerprint(), actorId, clock.instant());
        // Flush now: a duplicate key fails (or waits for the other request) here, before
        // any balance is locked.
        transactions.saveAndFlush(transaction);
        history.save(TransactionStatusChange.creation(transaction));
        history.save(transaction.start(actorId, clock.instant()));

        MoneyMovement movement = handlers.get(transaction.getType()).movementOf(transaction, locked);
        Map<UUID, LedgerBalance> balances = ledger.lockForPosting(movement.ledgerAccountIds());
        try {
            checkRules(transaction, movement, balances);
        } catch (BusinessRuleViolationException rule) {
            history.save(transaction.reject(rule.getErrorCode().code(), rule.getMessage(), actorId,
                    clock.instant()));
            log.info("Transaction {} ({}) rejected: {}", transaction.getId(), transaction.getType(),
                    rule.getErrorCode().code());
            return transaction;
        }

        PostedJournalEntry entry = ledger.post(movement.toJournalEntry(transaction.getAmount(),
                transaction.getId().toString(), actorId));
        history.save(transaction.complete(entry.journalEntryId(), actorId, clock.instant()));
        log.info("Transaction {} ({} {}) completed with journal entry {}", transaction.getId(),
                transaction.getType(), transaction.getAmount(), entry.journalEntryId());
        return transaction;
    }

    /**
     * The rules every movement must pass, for each customer account it touches: the status
     * allows the direction, the currency matches, and a debited account has the funds.
     * Plain Java on locked data, so the answer cannot change before the posting.
     *
     * @throws BusinessRuleViolationException the first rule that fails
     */
    private void checkRules(Transaction transaction, MoneyMovement movement,
            Map<UUID, LedgerBalance> balances) {
        for (Leg leg : movement.legs()) {
            OperableAccount account = leg.customerAccount();
            if (account == null) {
                continue;
            }
            if (leg.direction() == EntryDirection.DEBIT) {
                account.requireDebitable();
            } else {
                account.requireCreditable();
            }
            if (account.currency() != transaction.getCurrency()) {
                throw new BusinessRuleViolationException(TransactionsErrorCode.CURRENCY_MISMATCH,
                        "The account is in %s, but the transaction is in %s."
                                .formatted(account.currency(), transaction.getCurrency()));
            }
            if (leg.direction() == EntryDirection.DEBIT) {
                availableFunds.require(account, balances.get(account.ledgerAccountId()),
                        transaction.getAmount());
            }
        }
    }

    private static Map<TransactionType, OperationHandler> byType(List<OperationHandler> handlers) {
        Map<TransactionType, OperationHandler> byType = new EnumMap<>(TransactionType.class);
        for (OperationHandler handler : handlers) {
            if (byType.put(handler.type(), handler) != null) {
                throw new IllegalStateException("Two handlers for " + handler.type());
            }
        }
        for (TransactionType type : TransactionType.values()) {
            if (!byType.containsKey(type)) {
                throw new IllegalStateException("No handler for " + type);
            }
        }
        return byType;
    }
}
