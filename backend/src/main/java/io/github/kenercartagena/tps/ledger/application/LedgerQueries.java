package io.github.kenercartagena.tps.ledger.application;

import io.github.kenercartagena.tps.ledger.Ledger;
import io.github.kenercartagena.tps.ledger.domain.JournalEntryRepository;
import io.github.kenercartagena.tps.ledger.domain.LedgerAccountRepository;
import io.github.kenercartagena.tps.ledger.domain.LedgerErrorCode;
import io.github.kenercartagena.tps.shared.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only views of the ledger for its REST API (auditors). */
@Service
@Transactional(readOnly = true)
public class LedgerQueries {

    private final LedgerAccountRepository accounts;
    private final JournalEntryRepository journalEntries;
    private final Ledger ledger;

    LedgerQueries(LedgerAccountRepository accounts, JournalEntryRepository journalEntries,
            Ledger ledger) {
        this.accounts = accounts;
        this.journalEntries = journalEntries;
        this.ledger = ledger;
    }

    /** One page of ledger accounts, sorted by code. */
    public Page<LedgerAccountView> listAccounts(int page, int size) {
        return accounts.findAll(PageRequest.of(page, size, Sort.by("code")))
                .map(LedgerAccountView::of);
    }

    public LedgerAccountView getAccount(UUID ledgerAccountId) {
        return accounts.findById(ledgerAccountId).map(LedgerAccountView::of)
                .orElseThrow(() -> LedgerService.notFound(ledgerAccountId));
    }

    /** The postings of an account, newest first. */
    public Page<LedgerPostingView> postings(UUID ledgerAccountId, int page, int size) {
        return ledger.postingsOf(ledgerAccountId, page, size).map(LedgerPostingView::of);
    }

    public JournalEntryView getJournalEntry(UUID journalEntryId) {
        return journalEntries.findWithPostingsById(journalEntryId).map(JournalEntryView::of)
                .orElseThrow(() -> new ResourceNotFoundException(LedgerErrorCode.JOURNAL_ENTRY_NOT_FOUND,
                        "Journal entry '%s' was not found.".formatted(journalEntryId)));
    }
}
