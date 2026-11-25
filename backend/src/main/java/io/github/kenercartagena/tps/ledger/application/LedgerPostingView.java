package io.github.kenercartagena.tps.ledger.application;

import io.github.kenercartagena.tps.ledger.EntryDirection;
import io.github.kenercartagena.tps.ledger.PostingView;
import io.github.kenercartagena.tps.shared.money.Currency;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Read model of one posting in the history of a ledger account, returned by the API.
 * Amounts are decimal strings.
 */
public record LedgerPostingView(
        UUID postingId,
        UUID journalEntryId,
        int lineNumber,
        EntryDirection direction,
        String amount,
        String balanceAfter,
        Currency currency,
        String description,
        @Nullable String reference,
        Instant postedAt) {

    static LedgerPostingView of(PostingView posting) {
        return new LedgerPostingView(posting.postingId(), posting.journalEntryId(),
                posting.lineNumber(), posting.direction(), posting.amount().toPlainString(),
                posting.balanceAfter().toPlainString(), posting.amount().currency(),
                posting.description(), posting.reference(), posting.postedAt());
    }
}
