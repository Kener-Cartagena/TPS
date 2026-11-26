package io.github.kenercartagena.tps.transactions.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kenercartagena.tps.shared.money.Currency;
import io.github.kenercartagena.tps.shared.money.Money;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** The Transaction aggregate: what it accepts at creation and how its status moves. */
class TransactionTests {

    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-25T15:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(1);

    @Test
    void aNewTransactionIsCreatedWithWhatWasRequested() {
        Transaction deposit = deposit("1250.50");

        assertThat(deposit.getStatus()).isEqualTo(TransactionStatus.CREATED);
        assertThat(deposit.getAmount()).isEqualTo(Money.of("1250.50", Currency.HNL));
        assertThat(deposit.getDestinationAccountId()).isEqualTo(ACCOUNT);
        assertThat(deposit.getSourceAccountId()).isNull();
        assertThat(deposit.getCreatedBy()).isEqualTo(ACTOR);
        assertThat(deposit.getProcessedAt()).isNull();
    }

    @Test
    void theAmountMustBePositive() {
        assertThatThrownBy(() -> deposit("0.00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> deposit("-5.00")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eachTypeUsesItsOwnAccounts() {
        assertThatThrownBy(() -> Transaction.create(TransactionType.DEPOSIT, hnl("1.00"), ACCOUNT,
                null, null, key(), fingerprint(), ACTOR, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Transaction.create(TransactionType.WITHDRAWAL, hnl("1.00"), null,
                ACCOUNT, null, key(), fingerprint(), ACTOR, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void completingRecordsTheJournalEntryAndEachStep() {
        Transaction deposit = saved(deposit("10.00"));
        UUID entry = UUID.randomUUID();

        TransactionStatusChange started = deposit.start(ACTOR, NOW);
        TransactionStatusChange completed = deposit.complete(entry, ACTOR, LATER);

        assertThat(started.getFromStatus()).isEqualTo(TransactionStatus.CREATED);
        assertThat(started.getToStatus()).isEqualTo(TransactionStatus.PROCESSING);
        assertThat(completed.getFromStatus()).isEqualTo(TransactionStatus.PROCESSING);
        assertThat(completed.getToStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(completed.getChangedBy()).isEqualTo(ACTOR);
        assertThat(deposit.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(deposit.getJournalEntryId()).isEqualTo(entry);
        assertThat(deposit.getProcessedAt()).isEqualTo(LATER);
        assertThat(deposit.getRejectionCode()).isNull();
    }

    @Test
    void rejectingRecordsTheRuleAndMovesNoMoney() {
        Transaction withdrawal = saved(withdrawal("10.00"));
        withdrawal.start(ACTOR, NOW);

        TransactionStatusChange rejected = withdrawal.reject("INSUFFICIENT_FUNDS",
                "The account does not have enough funds.", ACTOR, LATER);

        assertThat(rejected.getToStatus()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(rejected.getReason()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(withdrawal.getRejectionCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(withdrawal.getRejectionDetail()).isEqualTo("The account does not have enough funds.");
        assertThat(withdrawal.getJournalEntryId()).isNull();
        assertThat(withdrawal.getProcessedAt()).isEqualTo(LATER);
    }

    @Test
    void anOutcomeIsFinal() {
        Transaction deposit = saved(deposit("10.00"));
        deposit.start(ACTOR, NOW);
        deposit.reject("ACCOUNT_SUSPENDED", "Suspended.", ACTOR, NOW);

        assertThatThrownBy(() -> deposit.complete(UUID.randomUUID(), ACTOR, LATER))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> deposit.reject("X", "Again.", ACTOR, LATER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aTransactionCannotCompleteWithoutBeingProcessed() {
        Transaction deposit = saved(deposit("10.00"));

        assertThatThrownBy(() -> deposit.complete(UUID.randomUUID(), ACTOR, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CREATED to COMPLETED");
    }

    @Test
    void aLongRejectionDetailIsShortenedToFitItsColumn() {
        Transaction deposit = saved(deposit("10.00"));
        deposit.start(ACTOR, NOW);

        deposit.reject("SOME_RULE", "x".repeat(400), ACTOR, NOW);

        assertThat(deposit.getRejectionDetail()).hasSize(Transaction.MAX_REJECTION_DETAIL_LENGTH);
    }

    // ---- Helpers -------------------------------------------------------------------

    private static Transaction deposit(String amount) {
        return Transaction.create(TransactionType.DEPOSIT, hnl(amount), null, ACCOUNT, null, key(),
                fingerprint(), ACTOR, NOW);
    }

    private static Transaction withdrawal(String amount) {
        return Transaction.create(TransactionType.WITHDRAWAL, hnl(amount), ACCOUNT, null, null, key(),
                fingerprint(), ACTOR, NOW);
    }

    /** The id JPA assigns on save, which every history record needs. */
    private static Transaction saved(Transaction transaction) {
        ReflectionTestUtils.setField(transaction, "id", UUID.randomUUID());
        return transaction;
    }

    private static Money hnl(String amount) {
        return Money.of(amount, Currency.HNL);
    }

    private static IdempotencyKey key() {
        return new IdempotencyKey(UUID.randomUUID().toString());
    }

    private static RequestFingerprint fingerprint() {
        return RequestFingerprint.of("test");
    }
}
