package io.github.kenercartagena.tps.ledger;

import static io.github.kenercartagena.tps.ledger.JournalLine.credit;
import static io.github.kenercartagena.tps.ledger.JournalLine.debit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kenercartagena.tps.TestcontainersConfiguration;
import io.github.kenercartagena.tps.ledger.application.JournalEntryView;
import io.github.kenercartagena.tps.ledger.application.LedgerAccountView;
import io.github.kenercartagena.tps.ledger.application.LedgerQueries;
import io.github.kenercartagena.tps.ledger.application.LedgerVerificationReport;
import io.github.kenercartagena.tps.ledger.application.LedgerVerifier;
import io.github.kenercartagena.tps.shared.error.BusinessRuleViolationException;
import io.github.kenercartagena.tps.shared.error.ConflictException;
import io.github.kenercartagena.tps.shared.error.ResourceNotFoundException;
import io.github.kenercartagena.tps.shared.error.TpsException;
import io.github.kenercartagena.tps.shared.money.Currency;
import io.github.kenercartagena.tps.shared.money.Money;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The ledger facade against a real PostgreSQL database: posting, balances, history,
 * rejected entries, closing and the verification report. Every test opens its own
 * accounts, so tests are independent although they share the database.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LedgerIntegrationTests {

    @Autowired
    private Ledger ledger;

    @Autowired
    private LedgerQueries queries;

    @Autowired
    private LedgerVerifier verifier;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TransactionTemplate transactions;

    // ---- System accounts -----------------------------------------------------------

    @Test
    void settlementAccountsAreCreatedByTheMigration() {
        for (Currency currency : Currency.values()) {
            LedgerAccountView account = queries.getAccount(SystemLedgerAccounts.settlement(currency));

            assertThat(account.code()).isEqualTo("SETTLEMENT-" + currency);
            assertThat(account.type()).isEqualTo(LedgerAccountType.ASSET);
            assertThat(account.currency()).isEqualTo(currency);
            assertThat(account.allowNegativeBalance()).isFalse();
        }
    }

    // ---- Posting -------------------------------------------------------------------

    @Test
    void postingUpdatesBalancesAndRecordsTheHistory() {
        UUID bank = openAccount(LedgerAccountType.ASSET, Currency.HNL, false);
        UUID customer = openAccount(LedgerAccountType.LIABILITY, Currency.HNL, false);

        PostedJournalEntry deposit = ledger.post(entry("Cash deposit",
                debit(bank, hnl("100.00")), credit(customer, hnl("100.00"))));
        ledger.post(entry("Cash withdrawal", debit(customer, hnl("30.25")), credit(bank, hnl("30.25"))));

        assertThat(ledger.balanceOf(customer).balance()).isEqualTo(hnl("69.75"));
        assertThat(ledger.balanceOf(bank).balance()).isEqualTo(hnl("69.75"));

        Page<PostingView> history = ledger.postingsOf(customer, 0, 20);
        assertThat(history.getTotalElements()).isEqualTo(2);
        assertThat(history.getContent()).extracting(PostingView::description)
                .containsExactly("Cash withdrawal", "Cash deposit");
        assertThat(history.getContent()).extracting(PostingView::direction)
                .containsExactly(EntryDirection.DEBIT, EntryDirection.CREDIT);
        assertThat(history.getContent()).extracting(PostingView::balanceAfter)
                .containsExactly(hnl("69.75"), hnl("100.00"));
        assertThat(history.getContent().getLast().journalEntryId()).isEqualTo(deposit.journalEntryId());
    }

    @Test
    void journalEntryIsReadWithAllItsLines() {
        UUID bank = openAccount(LedgerAccountType.ASSET, Currency.USD, false);
        UUID customer = openAccount(LedgerAccountType.LIABILITY, Currency.USD, false);

        PostedJournalEntry posted = ledger.post(new JournalEntryRequest("Wire received", "TX-42", null,
                List.of(debit(bank, usd("12.50")), credit(customer, usd("12.50")))));

        JournalEntryView entry = queries.getJournalEntry(posted.journalEntryId());
        assertThat(entry.reference()).isEqualTo("TX-42");
        assertThat(entry.currency()).isEqualTo(Currency.USD);
        assertThat(entry.lines()).extracting(JournalEntryView.Line::ledgerAccountId).containsExactly(bank, customer);
        assertThat(entry.lines()).extracting(JournalEntryView.Line::amount).containsOnly("12.50");
    }

    // ---- Rejected entries leave no trace -------------------------------------------

    @Test
    void rejectedEntriesRecordNothing() {
        UUID bank = openAccount(LedgerAccountType.ASSET, Currency.HNL, false);
        UUID customer = openAccount(LedgerAccountType.LIABILITY, Currency.HNL, false);
        UUID dollars = openAccount(LedgerAccountType.LIABILITY, Currency.USD, false);
        ledger.post(entry("Funding", debit(bank, hnl("50.00")), credit(customer, hnl("50.00"))));

        assertRejected("UNBALANCED_JOURNAL_ENTRY",
                entry("Unbalanced", debit(bank, hnl("10.00")), credit(customer, hnl("9.00"))));
        assertRejected("INVALID_JOURNAL_ENTRY", entry("One line", debit(bank, hnl("10.00"))));
        assertRejected("CURRENCY_MISMATCH",
                entry("Wrong currency", debit(dollars, hnl("1.00")), credit(customer, hnl("1.00"))));
        assertRejected("INSUFFICIENT_FUNDS",
                entry("Overdraft", debit(customer, hnl("50.01")), credit(bank, hnl("50.01"))));
        assertThatThrownBy(() -> ledger.post(entry("Unknown account",
                debit(UUID.randomUUID(), hnl("1.00")), credit(customer, hnl("1.00")))))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(ledger.balanceOf(customer).balance()).isEqualTo(hnl("50.00"));
        assertThat(ledger.balanceOf(bank).balance()).isEqualTo(hnl("50.00"));
        assertThat(ledger.postingsOf(customer, 0, 20).getTotalElements()).isEqualTo(1);
    }

    // ---- Accounts ------------------------------------------------------------------

    @Test
    void ledgerAccountCodesAreUnique() {
        String code = uniqueCode();
        ledger.openAccount(new NewLedgerAccount(code, "First", LedgerAccountType.ASSET, Currency.HNL, false));

        assertThatThrownBy(() -> ledger.openAccount(
                new NewLedgerAccount(code, "Second", LedgerAccountType.ASSET, Currency.HNL, false)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(code);
    }

    @Test
    void onlyAnEmptyAccountCanBeClosedAndThenItAcceptsNoPostings() {
        UUID bank = openAccount(LedgerAccountType.ASSET, Currency.HNL, false);
        UUID customer = openAccount(LedgerAccountType.LIABILITY, Currency.HNL, false);
        ledger.post(entry("Deposit", debit(bank, hnl("5.00")), credit(customer, hnl("5.00"))));

        assertThat(ledger.closeIfEmpty(customer)).isFalse();

        ledger.post(entry("Withdrawal", debit(customer, hnl("5.00")), credit(bank, hnl("5.00"))));
        assertThat(ledger.closeIfEmpty(customer)).isTrue();
        assertThat(queries.getAccount(customer).closedAt()).isNotNull();
        assertRejected("LEDGER_ACCOUNT_CLOSED",
                entry("After closing", debit(bank, hnl("1.00")), credit(customer, hnl("1.00"))));
    }

    @Test
    void unknownAccountsAreNotFound() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> ledger.balanceOf(unknown)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> ledger.postingsOf(unknown, 0, 20)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> ledger.closeIfEmpty(unknown)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- Verification --------------------------------------------------------------

    @Test
    void verificationFindsTheLedgerConsistent() {
        UUID bank = openAccount(LedgerAccountType.ASSET, Currency.HNL, false);
        UUID customer = openAccount(LedgerAccountType.LIABILITY, Currency.HNL, false);
        ledger.post(entry("Deposit", debit(bank, hnl("75.00")), credit(customer, hnl("75.00"))));

        LedgerVerificationReport report = verifier.verify();

        assertThat(report.consistent()).isTrue();
        assertThat(report.journalEntriesChecked()).isPositive();
        assertThat(report.accountsChecked()).isGreaterThanOrEqualTo(4);
        assertThat(report.unbalancedJournalEntries()).isEmpty();
        assertThat(report.accountMismatches()).isEmpty();
        assertThat(report.totals()).allMatch(LedgerVerificationReport.CurrencyTotals::balanced);
    }

    /**
     * A balance changed behind the application's back is detected. The change is made in a
     * transaction that is rolled back, so the shared database stays consistent.
     */
    @Test
    void verificationDetectsABalanceChangedWithSql() {
        UUID bank = openAccount(LedgerAccountType.ASSET, Currency.HNL, false);
        UUID customer = openAccount(LedgerAccountType.LIABILITY, Currency.HNL, false);
        ledger.post(entry("Deposit", debit(bank, hnl("20.00")), credit(customer, hnl("20.00"))));

        LedgerVerificationReport report = transactions.execute(status -> {
            jdbc.sql("UPDATE ledger_accounts SET balance = balance + 1000 WHERE id = :id")
                    .param("id", customer).update();
            status.setRollbackOnly();
            return verifier.verify();
        });

        assertThat(report.consistent()).isFalse();
        assertThat(report.accountMismatches()).singleElement().satisfies(mismatch -> {
            assertThat(mismatch.ledgerAccountId()).isEqualTo(customer);
            assertThat(mismatch.storedBalance()).isEqualTo("1020.00");
            assertThat(mismatch.balanceFromPostings()).isEqualTo("20.00");
            assertThat(mismatch.lastBalanceAfter()).isEqualTo("20.00");
        });
        assertThat(verifier.verify().consistent()).as("after the rollback").isTrue();
    }

    // ---- Helpers -------------------------------------------------------------------

    private void assertRejected(String code, JournalEntryRequest request) {
        assertThatThrownBy(() -> ledger.post(request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting(ex -> ((TpsException) ex).getErrorCode().code())
                .isEqualTo(code);
    }

    private UUID openAccount(LedgerAccountType type, Currency currency, boolean allowNegative) {
        return ledger.openAccount(new NewLedgerAccount(uniqueCode(), "Test account", type, currency,
                allowNegative));
    }

    static String uniqueCode() {
        return "TEST-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase(Locale.ROOT);
    }

    private static JournalEntryRequest entry(String description, JournalLine... lines) {
        return new JournalEntryRequest(description, null, null, List.of(lines));
    }

    private static Money hnl(String amount) {
        return Money.of(amount, Currency.HNL);
    }

    private static Money usd(String amount) {
        return Money.of(amount, Currency.USD);
    }
}
