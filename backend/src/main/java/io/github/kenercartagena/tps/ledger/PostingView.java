package io.github.kenercartagena.tps.ledger;

import io.github.kenercartagena.tps.shared.money.Money;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One posting in the history of a ledger account, like a line of a bank statement.
 *
 * @param balanceAfter balance of the account right after this posting
 * @param description  description of the journal entry the posting belongs to
 * @param reference    reference of that journal entry, if any
 */
public record PostingView(
        UUID postingId,
        UUID journalEntryId,
        int lineNumber,
        EntryDirection direction,
        Money amount,
        Money balanceAfter,
        String description,
        @Nullable String reference,
        Instant postedAt) {
}
