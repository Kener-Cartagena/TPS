package io.github.kenercartagena.tps.ledger.application;

import io.github.kenercartagena.tps.shared.money.Currency;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Result of checking the whole ledger against its own postings. Amounts are decimal
 * strings. Each list of problems holds at most {@value LedgerVerifier#MAX_PROBLEMS_LISTED}
 * items.
 *
 * @param consistent               {@code true} when no problem was found
 * @param unbalancedJournalEntries entries with fewer than two lines, more than one currency,
 *                                 or debits different from credits
 * @param accountMismatches        accounts whose stored balance differs from the sum of their
 *                                 postings or from the balance after their last posting
 * @param totals                   total debits and credits of all postings, per currency
 */
public record LedgerVerificationReport(
        Instant verifiedAt,
        boolean consistent,
        long journalEntriesChecked,
        long accountsChecked,
        List<UUID> unbalancedJournalEntries,
        List<AccountMismatch> accountMismatches,
        List<CurrencyTotals> totals) {

    /**
     * @param lastBalanceAfter balance recorded by the account's last posting; {@code null}
     *                         if it has none
     */
    public record AccountMismatch(
            UUID ledgerAccountId,
            String code,
            Currency currency,
            String storedBalance,
            String balanceFromPostings,
            @Nullable String lastBalanceAfter) {
    }

    public record CurrencyTotals(
            Currency currency,
            String totalDebits,
            String totalCredits,
            boolean balanced) {
    }
}
