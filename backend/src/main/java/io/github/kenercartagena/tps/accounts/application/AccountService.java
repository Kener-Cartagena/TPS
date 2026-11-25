package io.github.kenercartagena.tps.accounts.application;

import io.github.kenercartagena.tps.accounts.domain.Account;
import io.github.kenercartagena.tps.accounts.domain.AccountHolderRepository;
import io.github.kenercartagena.tps.accounts.domain.AccountNumber;
import io.github.kenercartagena.tps.accounts.domain.AccountRepository;
import io.github.kenercartagena.tps.accounts.domain.AccountStatus;
import io.github.kenercartagena.tps.accounts.domain.AccountStatusChange;
import io.github.kenercartagena.tps.accounts.domain.AccountStatusChangeRepository;
import io.github.kenercartagena.tps.accounts.domain.AccountsErrorCode;
import io.github.kenercartagena.tps.ledger.Ledger;
import io.github.kenercartagena.tps.ledger.LedgerAccountType;
import io.github.kenercartagena.tps.ledger.LedgerBalance;
import io.github.kenercartagena.tps.ledger.NewLedgerAccount;
import io.github.kenercartagena.tps.shared.error.BusinessRuleViolationException;
import io.github.kenercartagena.tps.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account use cases. Authorization (who may call what) is enforced by the web layer with
 * permissions; this class enforces the rules that apply whoever the caller is.
 *
 * <p>Every account is backed by its own ledger account, opened in the same database
 * transaction: there is never an account without a ledger account, or the reverse.
 */
@Service
@Transactional
public class AccountService {

    /** Prefix of the ledger account code of a customer account: {@code CUS-<number>}. */
    static final String LEDGER_CODE_PREFIX = "CUS-";

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accounts;
    private final AccountHolderRepository holders;
    private final AccountStatusChangeRepository statusChanges;
    private final Ledger ledger;
    private final Clock clock;

    AccountService(AccountRepository accounts, AccountHolderRepository holders,
            AccountStatusChangeRepository statusChanges, Ledger ledger, Clock clock) {
        this.accounts = accounts;
        this.holders = holders;
        this.statusChanges = statusChanges;
        this.ledger = ledger;
        this.clock = clock;
    }

    // ---- Commands ------------------------------------------------------------------

    /** Opens an active account with a zero balance, and records the opening in its history. */
    public AccountView open(OpenAccountCommand command, UUID actorId) {
        if (!holders.existsById(command.holderId())) {
            throw AccountHolderService.notFound(command.holderId());
        }
        AccountNumber number = AccountNumber.fromSequence(accounts.nextAccountNumberSequence());
        UUID ledgerAccountId = ledger.openAccount(new NewLedgerAccount(
                LEDGER_CODE_PREFIX + number, "Customer account " + number,
                LedgerAccountType.LIABILITY, command.currency(), false));

        Account account = Account.open(number, command.holderId(), command.type(),
                command.currency(), ledgerAccountId, actorId, clock.instant());
        accounts.save(account);
        statusChanges.save(AccountStatusChange.opening(account, actorId));

        log.info("Account {} ({} {}) opened for holder {} by {}", account.getId(),
                account.getType(), account.getCurrency(), account.getHolderId(), actorId);
        return AccountView.of(account);
    }

    /**
     * Changes the status of an account and records why. The account is locked first, so
     * concurrent changes are applied one after the other, each checked against the status
     * left by the previous one.
     *
     * <p>Closing is final and requires a zero balance; it also closes the ledger account,
     * so no money can be posted to it afterwards.
     */
    public AccountView changeStatus(UUID accountId, AccountStatus target, String reason,
            UUID actorId) {
        Account account = accounts.lockById(accountId).orElseThrow(() -> notFound(accountId));
        account.ensureCanChangeTo(target);
        if (target == AccountStatus.CLOSED && !ledger.closeIfEmpty(account.getLedgerAccountId())) {
            throw new BusinessRuleViolationException(AccountsErrorCode.ACCOUNT_HAS_BALANCE,
                    "Only an account with a zero balance can be closed.");
        }

        AccountStatus previous = account.getStatus();
        statusChanges.save(account.changeStatus(target, reason, actorId, clock.instant()));
        log.info("Status of account {} changed from {} to {} by {}", accountId, previous, target,
                actorId);
        return AccountView.of(account);
    }

    // ---- Queries -------------------------------------------------------------------

    @Transactional(readOnly = true)
    public AccountView get(UUID accountId) {
        return AccountView.of(find(accountId));
    }

    /** One page of accounts matching the filter, sorted by account number (opening order). */
    @Transactional(readOnly = true)
    public Page<AccountView> list(AccountFilter filter, int page, int size) {
        return accounts.findAll(filter.toSpecification(),
                        PageRequest.of(page, size, Sort.by("accountNumber")))
                .map(AccountView::of);
    }

    @Transactional(readOnly = true)
    public AccountBalanceView balance(UUID accountId) {
        Account account = find(accountId);
        Instant asOf = clock.instant();
        LedgerBalance balance = ledger.balanceOf(account.getLedgerAccountId());
        String amount = balance.balance().toPlainString();
        // No holds exist yet (Phases 4 and 6), so all of the balance is available.
        return new AccountBalanceView(account.getId(), account.getAccountNumber().value(),
                account.getCurrency(), amount, amount, asOf);
    }

    /** The movements of an account, newest first. */
    @Transactional(readOnly = true)
    public Page<AccountEntryView> entries(UUID accountId, int page, int size) {
        Account account = find(accountId);
        return ledger.postingsOf(account.getLedgerAccountId(), page, size).map(AccountEntryView::of);
    }

    /** The status history of an account, newest first. */
    @Transactional(readOnly = true)
    public Page<AccountStatusChangeView> statusHistory(UUID accountId, int page, int size) {
        if (!accounts.existsById(accountId)) {
            throw notFound(accountId);
        }
        PageRequest request = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "changedAt", "id"));
        return statusChanges.findByAccountId(accountId, request).map(AccountStatusChangeView::of);
    }

    // ---- Helpers -------------------------------------------------------------------

    private Account find(UUID accountId) {
        return accounts.findById(accountId).orElseThrow(() -> notFound(accountId));
    }

    private static ResourceNotFoundException notFound(UUID accountId) {
        return new ResourceNotFoundException(AccountsErrorCode.ACCOUNT_NOT_FOUND,
                "Account '%s' was not found.".formatted(accountId));
    }
}
