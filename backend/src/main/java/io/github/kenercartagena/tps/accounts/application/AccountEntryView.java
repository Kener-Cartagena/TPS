package io.github.kenercartagena.tps.accounts.application;

import io.github.kenercartagena.tps.ledger.EntryDirection;
import io.github.kenercartagena.tps.ledger.PostingView;
import io.github.kenercartagena.tps.shared.money.Currency;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One movement of an account, like a line of a bank statement. Amounts are decimal strings.
 *
 * @param direction    {@code CREDIT} adds to the account's balance (money in), {@code DEBIT}
 *                     subtracts from it (money out), as on a bank statement
 * @param balanceAfter the account's balance right after this movement
 */
public record AccountEntryView(
        UUID entryId,
        UUID journalEntryId,
        EntryDirection direction,
        String amount,
        String balanceAfter,
        Currency currency,
        String description,
        @Nullable String reference,
        Instant postedAt) {

    static AccountEntryView of(PostingView posting) {
        return new AccountEntryView(posting.postingId(), posting.journalEntryId(),
                posting.direction(), posting.amount().toPlainString(),
                posting.balanceAfter().toPlainString(), posting.amount().currency(),
                posting.description(), posting.reference(), posting.postedAt());
    }
}
