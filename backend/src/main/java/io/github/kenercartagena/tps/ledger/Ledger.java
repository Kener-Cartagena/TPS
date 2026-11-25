package io.github.kenercartagena.tps.ledger;

import java.util.UUID;
import org.springframework.data.domain.Page;

/**
 * The general ledger: the public API of the ledger module.
 *
 * <p>Every method joins the caller's database transaction, if there is one. A business
 * operation (e.g. a transfer) and its journal entry therefore commit or roll back
 * together: there is never a transfer without its entry, nor an entry without its
 * transfer.
 */
public interface Ledger {

    /**
     * Opens a ledger account with a zero balance.
     *
     * @return the id of the new account
     * @throws io.github.kenercartagena.tps.shared.error.ConflictException if the code is taken
     */
    UUID openAccount(NewLedgerAccount account);

    /**
     * Records a journal entry and updates the balances of its accounts, atomically.
     *
     * <p>The entry is rejected ({@code 422}), and nothing is recorded, if it has fewer than
     * two lines, a non-positive amount, more than one currency, debits different from
     * credits, an account in another currency or closed, or if it would leave an account
     * that does not allow it with a negative balance ({@code INSUFFICIENT_FUNDS}).
     *
     * <p>Concurrent entries on the same accounts are serialized: each one locks its
     * accounts (always in the same order, so two entries cannot deadlock) until its
     * transaction ends.
     *
     * @throws io.github.kenercartagena.tps.shared.error.ResourceNotFoundException if an
     *         account does not exist
     */
    PostedJournalEntry post(JournalEntryRequest entry);

    /**
     * The current balance of an account.
     *
     * @throws io.github.kenercartagena.tps.shared.error.ResourceNotFoundException if the
     *         account does not exist
     */
    LedgerBalance balanceOf(UUID ledgerAccountId);

    /**
     * The postings of an account, newest first.
     *
     * @throws io.github.kenercartagena.tps.shared.error.ResourceNotFoundException if the
     *         account does not exist
     */
    Page<PostingView> postingsOf(UUID ledgerAccountId, int page, int size);

    /**
     * Closes an account if its balance is zero. A closed account accepts no more postings,
     * and closing is final.
     *
     * @return {@code true} if the account is closed (now or before), {@code false} if it
     *         was left open because its balance is not zero
     * @throws io.github.kenercartagena.tps.shared.error.ResourceNotFoundException if the
     *         account does not exist
     */
    boolean closeIfEmpty(UUID ledgerAccountId);
}
