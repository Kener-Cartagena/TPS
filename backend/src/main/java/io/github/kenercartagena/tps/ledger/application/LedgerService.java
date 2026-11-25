package io.github.kenercartagena.tps.ledger.application;

import io.github.kenercartagena.tps.ledger.JournalEntryRequest;
import io.github.kenercartagena.tps.ledger.JournalLine;
import io.github.kenercartagena.tps.ledger.Ledger;
import io.github.kenercartagena.tps.ledger.LedgerBalance;
import io.github.kenercartagena.tps.ledger.NewLedgerAccount;
import io.github.kenercartagena.tps.ledger.PostedJournalEntry;
import io.github.kenercartagena.tps.ledger.PostingView;
import io.github.kenercartagena.tps.ledger.domain.JournalEntry;
import io.github.kenercartagena.tps.ledger.domain.JournalEntryRepository;
import io.github.kenercartagena.tps.ledger.domain.LedgerAccount;
import io.github.kenercartagena.tps.ledger.domain.LedgerAccountRepository;
import io.github.kenercartagena.tps.ledger.domain.LedgerErrorCode;
import io.github.kenercartagena.tps.ledger.domain.Posting;
import io.github.kenercartagena.tps.ledger.domain.PostingRepository;
import io.github.kenercartagena.tps.shared.error.ConflictException;
import io.github.kenercartagena.tps.shared.error.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of the {@link Ledger} facade. The bookkeeping rules live in
 * {@link JournalEntry#post}; this class loads and locks the accounts, and stores the
 * result.
 */
@Service
@Transactional
class LedgerService implements Ledger {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final LedgerAccountRepository accounts;
    private final JournalEntryRepository journalEntries;
    private final PostingRepository postings;
    private final EntityManager entityManager;
    private final Clock clock;

    LedgerService(LedgerAccountRepository accounts, JournalEntryRepository journalEntries,
            PostingRepository postings, EntityManager entityManager, Clock clock) {
        this.accounts = accounts;
        this.journalEntries = journalEntries;
        this.postings = postings;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Override
    public UUID openAccount(NewLedgerAccount request) {
        if (accounts.existsByCode(request.code())) {
            throw codeTaken(request.code(), null);
        }
        LedgerAccount account = LedgerAccount.open(request.code(), request.name(), request.type(),
                request.currency(), request.allowNegativeBalance(), clock.instant());
        try {
            // Flush now so a concurrent insert of the same code fails here (409), not at commit.
            accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            throw codeTaken(request.code(), ex);
        }
        log.info("Ledger account {} opened ({} {})", account.getId(), account.getType(),
                account.getCurrency());
        return account.getId();
    }

    @Override
    public PostedJournalEntry post(JournalEntryRequest request) {
        Map<UUID, LedgerAccount> locked = lockAccountsOf(request);
        JournalEntry entry = JournalEntry.post(request, locked, clock.instant());
        journalEntries.save(entry);
        log.debug("Journal entry {} posted with {} lines", entry.getId(), entry.getPostings().size());
        return new PostedJournalEntry(entry.getId(), entry.getPostedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerBalance balanceOf(UUID ledgerAccountId) {
        LedgerAccount account = find(ledgerAccountId);
        return new LedgerBalance(account.getId(), account.getBalance());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostingView> postingsOf(UUID ledgerAccountId, int page, int size) {
        if (!accounts.existsById(ledgerAccountId)) {
            throw notFound(ledgerAccountId);
        }
        PageRequest request = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sequenceNumber"));
        return postings.findByLedgerAccountId(ledgerAccountId, request).map(LedgerService::toView);
    }

    @Override
    public boolean closeIfEmpty(UUID ledgerAccountId) {
        LedgerAccount account = lock(ledgerAccountId);
        boolean closed = account.closeIfEmpty(clock.instant());
        if (closed) {
            log.info("Ledger account {} closed", ledgerAccountId);
        }
        return closed;
    }

    // ---- Locking -------------------------------------------------------------------

    /**
     * Locks every account of the entry, in ascending id order. Two entries that touch the
     * same accounts always lock them in the same order, so they wait for each other
     * instead of deadlocking.
     */
    private Map<UUID, LedgerAccount> lockAccountsOf(JournalEntryRequest request) {
        Map<UUID, LedgerAccount> locked = new HashMap<>();
        request.lines().stream()
                .map(JournalLine::ledgerAccountId)
                .distinct()
                .sorted()
                .forEach(id -> locked.put(id, lock(id)));
        return locked;
    }

    /**
     * {@code SELECT ... FOR UPDATE} on one account, until the transaction ends.
     *
     * <p>{@code refresh} (not a locking query) because the account may already be in the
     * persistence context, read earlier in the same transaction: a locking query would
     * lock the row but keep that older, possibly stale balance. {@code refresh} always
     * reloads the row after locking it.
     */
    private LedgerAccount lock(UUID ledgerAccountId) {
        LedgerAccount account = find(ledgerAccountId);
        entityManager.refresh(account, LockModeType.PESSIMISTIC_WRITE);
        return account;
    }

    private LedgerAccount find(UUID ledgerAccountId) {
        return accounts.findById(ledgerAccountId).orElseThrow(() -> notFound(ledgerAccountId));
    }

    // ---- Helpers -------------------------------------------------------------------

    static PostingView toView(Posting posting) {
        JournalEntry entry = posting.getJournalEntry();
        return new PostingView(posting.getId(), entry.getId(), posting.getLineNumber(),
                posting.getDirection(), posting.getAmount(), posting.getBalanceAfter(),
                entry.getDescription(), entry.getReference(), posting.getPostedAt());
    }

    static ResourceNotFoundException notFound(UUID ledgerAccountId) {
        return new ResourceNotFoundException(LedgerErrorCode.LEDGER_ACCOUNT_NOT_FOUND,
                "Ledger account '%s' was not found.".formatted(ledgerAccountId));
    }

    private static ConflictException codeTaken(String code, @Nullable Throwable cause) {
        String message = "A ledger account with code '%s' already exists.".formatted(code);
        return cause == null
                ? new ConflictException(LedgerErrorCode.LEDGER_ACCOUNT_CODE_ALREADY_EXISTS, message)
                : new ConflictException(LedgerErrorCode.LEDGER_ACCOUNT_CODE_ALREADY_EXISTS, message, cause);
    }
}
