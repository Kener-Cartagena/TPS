package io.github.kenercartagena.tps.ledger;

import io.github.kenercartagena.tps.shared.money.Money;
import java.util.Objects;
import java.util.UUID;

/**
 * One line of a journal entry to post: an amount debited or credited to a ledger account.
 *
 * @param amount must be positive; the direction says whether it is a debit or a credit
 */
public record JournalLine(UUID ledgerAccountId, EntryDirection direction, Money amount) {

    public JournalLine {
        Objects.requireNonNull(ledgerAccountId, "ledgerAccountId");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(amount, "amount");
    }

    public static JournalLine debit(UUID ledgerAccountId, Money amount) {
        return new JournalLine(ledgerAccountId, EntryDirection.DEBIT, amount);
    }

    public static JournalLine credit(UUID ledgerAccountId, Money amount) {
        return new JournalLine(ledgerAccountId, EntryDirection.CREDIT, amount);
    }
}
