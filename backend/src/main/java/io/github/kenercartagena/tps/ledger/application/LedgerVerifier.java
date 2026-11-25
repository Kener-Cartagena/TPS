package io.github.kenercartagena.tps.ledger.application;

import io.github.kenercartagena.tps.ledger.application.LedgerVerificationReport.AccountMismatch;
import io.github.kenercartagena.tps.ledger.application.LedgerVerificationReport.CurrencyTotals;
import io.github.kenercartagena.tps.shared.money.Currency;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the ledger from first principles: it recomputes everything from the postings
 * and compares it with what is stored.
 *
 * <ol>
 *   <li>Every journal entry has at least two lines, one currency, and debits equal to
 *       credits.</li>
 *   <li>Every account's stored balance equals the sum of its postings, and the balance
 *       recorded by its last posting.</li>
 *   <li>Over all postings, total debits equal total credits in every currency.</li>
 * </ol>
 *
 * <p>The application and the database already prevent each of these problems; the
 * verification proves it, and would detect a change made behind their back (e.g. a balance
 * edited with SQL). The checks run in one {@code REPEATABLE READ} transaction, so they all
 * see the same snapshot even while postings are being added.
 *
 * <p>The queries scan the whole ledger: fine for this project's volumes. A production
 * ledger would verify incrementally (per period or since the last checkpoint).
 */
@Service
public class LedgerVerifier {

    /** Problems listed per category; the counts in the report are not limited. */
    static final int MAX_PROBLEMS_LISTED = 100;

    private static final Logger log = LoggerFactory.getLogger(LedgerVerifier.class);

    private static final String UNBALANCED_ENTRIES = """
            SELECT e.id
              FROM journal_entries e
              LEFT JOIN ledger_postings p ON p.journal_entry_id = e.id
             GROUP BY e.id
            HAVING count(p.id) < 2
                OR count(DISTINCT p.currency) <> 1
                OR coalesce(sum(p.amount) FILTER (WHERE p.direction = 'DEBIT'), 0)
                   <> coalesce(sum(p.amount) FILTER (WHERE p.direction = 'CREDIT'), 0)
             ORDER BY e.id
             LIMIT :limit""";

    /**
     * A posting on the normal side of its account (debit for ASSET and EXPENSE, credit for
     * the other types: see LedgerAccountType) adds to the balance; any other subtracts.
     */
    private static final String ACCOUNT_MISMATCHES = """
            WITH from_postings AS (
                SELECT a.id, a.code, a.currency, a.balance,
                       coalesce(sum(CASE
                               WHEN (p.direction = 'DEBIT') = (a.type IN ('ASSET', 'EXPENSE'))
                               THEN p.amount ELSE -p.amount END), 0) AS balance_from_postings
                  FROM ledger_accounts a
                  LEFT JOIN ledger_postings p ON p.ledger_account_id = a.id
                 GROUP BY a.id, a.code, a.currency, a.balance
            ), last_posting AS (
                SELECT DISTINCT ON (ledger_account_id) ledger_account_id, balance_after
                  FROM ledger_postings
                 ORDER BY ledger_account_id, sequence_number DESC
            )
            SELECT f.id, f.code, f.currency, f.balance, f.balance_from_postings,
                   l.balance_after AS last_balance_after
              FROM from_postings f
              LEFT JOIN last_posting l ON l.ledger_account_id = f.id
             WHERE f.balance <> f.balance_from_postings
                OR f.balance <> coalesce(l.balance_after, 0)
             ORDER BY f.code
             LIMIT :limit""";

    private static final String TOTALS = """
            SELECT currency,
                   coalesce(sum(amount) FILTER (WHERE direction = 'DEBIT'), 0)  AS debits,
                   coalesce(sum(amount) FILTER (WHERE direction = 'CREDIT'), 0) AS credits
              FROM ledger_postings
             GROUP BY currency
             ORDER BY currency""";

    private final JdbcClient jdbc;
    private final Clock clock;

    LedgerVerifier(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LedgerVerificationReport verify() {
        long entries = jdbc.sql("SELECT count(*) FROM journal_entries").query(Long.class).single();
        long accounts = jdbc.sql("SELECT count(*) FROM ledger_accounts").query(Long.class).single();

        List<UUID> unbalanced = jdbc.sql(UNBALANCED_ENTRIES)
                .param("limit", MAX_PROBLEMS_LISTED)
                .query(UUID.class).list();

        List<AccountMismatch> mismatches = jdbc.sql(ACCOUNT_MISMATCHES)
                .param("limit", MAX_PROBLEMS_LISTED)
                .query((rs, row) -> new AccountMismatch(
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        Currency.valueOf(rs.getString("currency")),
                        plain(rs.getBigDecimal("balance")),
                        plain(rs.getBigDecimal("balance_from_postings")),
                        rs.getBigDecimal("last_balance_after") == null
                                ? null : plain(rs.getBigDecimal("last_balance_after"))))
                .list();

        List<CurrencyTotals> totals = jdbc.sql(TOTALS)
                .query((rs, row) -> {
                    BigDecimal debits = rs.getBigDecimal("debits");
                    BigDecimal credits = rs.getBigDecimal("credits");
                    return new CurrencyTotals(Currency.valueOf(rs.getString("currency")),
                            plain(debits), plain(credits), debits.compareTo(credits) == 0);
                })
                .list();

        boolean consistent = unbalanced.isEmpty() && mismatches.isEmpty()
                && totals.stream().allMatch(CurrencyTotals::balanced);
        if (consistent) {
            log.info("Ledger verified: {} journal entries and {} accounts are consistent", entries, accounts);
        } else {
            log.error("Ledger verification FAILED: {} unbalanced entries, {} account mismatches, totals {}",
                    unbalanced.size(), mismatches.size(), totals);
        }
        return new LedgerVerificationReport(clock.instant(), consistent, entries, accounts,
                unbalanced, mismatches, totals);
    }

    private static String plain(BigDecimal amount) {
        return amount.setScale(2).toPlainString();
    }
}
