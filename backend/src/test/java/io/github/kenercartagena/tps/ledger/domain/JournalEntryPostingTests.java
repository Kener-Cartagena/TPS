package io.github.kenercartagena.tps.ledger.domain;

import static io.github.kenercartagena.tps.ledger.JournalLine.credit;
import static io.github.kenercartagena.tps.ledger.JournalLine.debit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kenercartagena.tps.ledger.EntryDirection;
import io.github.kenercartagena.tps.ledger.JournalEntryRequest;
import io.github.kenercartagena.tps.ledger.JournalLine;
import io.github.kenercartagena.tps.ledger.LedgerAccountType;
import io.github.kenercartagena.tps.shared.error.BusinessRuleViolationException;
import io.github.kenercartagena.tps.shared.money.Currency;
import io.github.kenercartagena.tps.shared.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * The bookkeeping rules of {@link JournalEntry#post}, without a database: what a valid
 * entry does to the balances, and that every invalid entry is rejected with the balances
 * left untouched.
 */
class JournalEntryPostingTests {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");

    private final UUID settlementId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID dollarsId = UUID.randomUUID();
    private final UUID overdraftId = UUID.randomUUID();

    /** Money TPS holds (ASSET) and money it owes a customer (LIABILITY), in lempiras. */
    private final LedgerAccount settlement = account("SETTLEMENT", LedgerAccountType.ASSET, Currency.HNL, false);
    private final LedgerAccount customer = account("CUSTOMER", LedgerAccountType.LIABILITY, Currency.HNL, false);
    private final LedgerAccount dollars = account("DOLLARS", LedgerAccountType.LIABILITY, Currency.USD, false);
    private final LedgerAccount overdraft = account("OVERDRAFT", LedgerAccountType.LIABILITY, Currency.HNL, true);

    private final Map<UUID, LedgerAccount> accounts = Map.of(settlementId, settlement,
            customerId, customer, dollarsId, dollars, overdraftId, overdraft);

    // ---- Valid entries -------------------------------------------------------------

    @Test
    void depositIncreasesBothSidesOnTheirNormalSide() {
        JournalEntry entry = post(debit(settlementId, hnl("100.00")), credit(customerId, hnl("100.00")));

        assertThat(settlement.getBalance()).isEqualTo(hnl("100.00"));
        assertThat(customer.getBalance()).isEqualTo(hnl("100.00"));
        assertThat(entry.getPostings()).extracting(Posting::getLineNumber).containsExactly(1, 2);
        assertThat(entry.getPostings()).extracting(Posting::getBalanceAfter)
                .containsExactly(hnl("100.00"), hnl("100.00"));
        assertThat(entry.getPostedAt()).isEqualTo(NOW);
    }

    @Test
    void withdrawalDecreasesBothSides() {
        post(debit(settlementId, hnl("100.00")), credit(customerId, hnl("100.00")));

        post(debit(customerId, hnl("30.25")), credit(settlementId, hnl("30.25")));

        assertThat(customer.getBalance()).isEqualTo(hnl("69.75"));
        assertThat(settlement.getBalance()).isEqualTo(hnl("69.75"));
    }

    @Test
    void entryWithSeveralLinesOnTheSameAccountRecordsTheRunningBalance() {
        JournalEntry entry = post(
                debit(settlementId, hnl("100.00")),
                credit(customerId, hnl("60.00")),
                credit(customerId, hnl("40.00")));

        assertThat(customer.getBalance()).isEqualTo(hnl("100.00"));
        assertThat(entry.getPostings()).extracting(Posting::getBalanceAfter)
                .containsExactly(hnl("100.00"), hnl("60.00"), hnl("100.00"));
    }

    @Test
    void accountThatAllowsItCanGoNegative() {
        post(debit(overdraftId, hnl("5.00")), credit(customerId, hnl("5.00")));

        assertThat(overdraft.getBalance()).isEqualTo(hnl("-5.00"));
    }

    @Test
    void descriptionAndReferenceAreKept() {
        JournalEntry entry = JournalEntry.post(new JournalEntryRequest("  Cash deposit  ", "TX-1", null,
                List.of(debit(settlementId, hnl("1")), credit(customerId, hnl("1")))), accounts, NOW);

        assertThat(entry.getDescription()).isEqualTo("Cash deposit");
        assertThat(entry.getReference()).isEqualTo("TX-1");
        assertThat(entry.getCreatedBy()).isNull();
    }

    // ---- Rejected entries: nothing changes -----------------------------------------

    @Test
    void unbalancedEntryIsRejected() {
        assertRejected(LedgerErrorCode.UNBALANCED_JOURNAL_ENTRY,
                () -> post(debit(settlementId, hnl("100.00")), credit(customerId, hnl("99.99"))));
    }

    @Test
    void entryNeedsAtLeastTwoLines() {
        assertRejected(LedgerErrorCode.INVALID_JOURNAL_ENTRY, () -> post(debit(settlementId, hnl("1"))));
        assertRejected(LedgerErrorCode.INVALID_JOURNAL_ENTRY, this::post);
    }

    @Test
    void amountsMustBePositive() {
        assertRejected(LedgerErrorCode.INVALID_JOURNAL_ENTRY,
                () -> post(debit(settlementId, hnl("0")), credit(customerId, hnl("0"))));
        assertRejected(LedgerErrorCode.INVALID_JOURNAL_ENTRY,
                () -> post(debit(settlementId, hnl("-5")), credit(customerId, hnl("-5"))));
    }

    @Test
    void entryCannotMixCurrencies() {
        assertRejected(LedgerErrorCode.INVALID_JOURNAL_ENTRY,
                () -> post(debit(settlementId, hnl("1")), credit(dollarsId, usd("1"))));
    }

    @Test
    void lineMustUseTheCurrencyOfItsAccount() {
        assertRejected(LedgerErrorCode.CURRENCY_MISMATCH,
                () -> post(debit(settlementId, usd("1")), credit(dollarsId, usd("1"))));
    }

    @Test
    void closedAccountAcceptsNoPostings() {
        assertThat(customer.closeIfEmpty(NOW)).isTrue();

        assertRejected(LedgerErrorCode.LEDGER_ACCOUNT_CLOSED,
                () -> post(debit(settlementId, hnl("1")), credit(customerId, hnl("1"))));
    }

    @Test
    void entryThatWouldOverdrawAnAccountIsRejected() {
        post(debit(settlementId, hnl("10.00")), credit(customerId, hnl("10.00")));

        assertRejected(LedgerErrorCode.INSUFFICIENT_FUNDS,
                () -> post(debit(customerId, hnl("10.01")), credit(settlementId, hnl("10.01"))));

        assertThat(customer.getBalance()).isEqualTo(hnl("10.00"));
        assertThat(settlement.getBalance()).isEqualTo(hnl("10.00"));
    }

    // ---- Ledger account rules ------------------------------------------------------

    @Test
    void normalSideDependsOnTheAccountType() {
        Money ten = hnl("10");

        assertThat(settlement.effectOf(EntryDirection.DEBIT, ten)).isEqualTo(ten);
        assertThat(settlement.effectOf(EntryDirection.CREDIT, ten)).isEqualTo(ten.negate());
        assertThat(customer.effectOf(EntryDirection.CREDIT, ten)).isEqualTo(ten);
        assertThat(customer.effectOf(EntryDirection.DEBIT, ten)).isEqualTo(ten.negate());
    }

    @Test
    void onlyAnEmptyAccountCanBeClosed() {
        post(debit(settlementId, hnl("1.00")), credit(customerId, hnl("1.00")));

        assertThat(customer.closeIfEmpty(NOW)).isFalse();
        assertThat(customer.isOpen()).isTrue();

        post(debit(customerId, hnl("1.00")), credit(settlementId, hnl("1.00")));
        assertThat(customer.closeIfEmpty(NOW)).isTrue();
        assertThat(customer.isOpen()).isFalse();
        assertThat(customer.getClosedAt()).isEqualTo(NOW);
        assertThat(customer.closeIfEmpty(NOW)).as("closing again").isTrue();
    }

    @Test
    void ledgerAccountCodesFollowTheDatabaseFormat() {
        assertThatThrownBy(() -> account("lowercase", LedgerAccountType.ASSET, Currency.HNL, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account("AB", LedgerAccountType.ASSET, Currency.HNL, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- Helpers -------------------------------------------------------------------

    private JournalEntry post(JournalLine... lines) {
        return JournalEntry.post(new JournalEntryRequest("Test entry", null, null, List.of(lines)),
                accounts, NOW);
    }

    /** The entry is rejected with the code, and no balance changed. */
    private void assertRejected(LedgerErrorCode code, Executable posting) {
        List<Money> before = balances();

        assertThatThrownBy(posting::execute)
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting(ex -> ((BusinessRuleViolationException) ex).getErrorCode())
                .isEqualTo(code);
        assertThat(balances()).as("balances after a rejected entry").isEqualTo(before);
    }

    private List<Money> balances() {
        return List.of(settlement.getBalance(), customer.getBalance(), dollars.getBalance(),
                overdraft.getBalance());
    }

    private static LedgerAccount account(String code, LedgerAccountType type, Currency currency,
            boolean allowNegative) {
        return LedgerAccount.open(code, code + " account", type, currency, allowNegative, NOW);
    }

    private static Money hnl(String amount) {
        return Money.of(amount, Currency.HNL);
    }

    private static Money usd(String amount) {
        return Money.of(amount, Currency.USD);
    }
}
