package io.github.kenercartagena.tps.ledger.application;

import io.github.kenercartagena.tps.ledger.EntryDirection;
import io.github.kenercartagena.tps.ledger.domain.JournalEntry;
import io.github.kenercartagena.tps.ledger.domain.Posting;
import io.github.kenercartagena.tps.shared.money.Currency;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Read model of a journal entry with all its lines, returned by the API.
 *
 * @param createdBy user who caused the entry, or {@code null} for the system
 */
public record JournalEntryView(
        UUID id,
        String description,
        @Nullable String reference,
        Currency currency,
        Instant postedAt,
        @Nullable UUID createdBy,
        List<Line> lines) {

    /** One line of the entry. Amounts are decimal strings. */
    public record Line(
            int lineNumber,
            UUID ledgerAccountId,
            EntryDirection direction,
            String amount,
            String balanceAfter) {

        static Line of(Posting posting) {
            return new Line(posting.getLineNumber(), posting.getLedgerAccountId(),
                    posting.getDirection(), posting.getAmount().toPlainString(),
                    posting.getBalanceAfter().toPlainString());
        }
    }

    static JournalEntryView of(JournalEntry entry) {
        List<Posting> postings = entry.getPostings();
        return new JournalEntryView(entry.getId(), entry.getDescription(), entry.getReference(),
                postings.getFirst().getAmount().currency(), entry.getPostedAt(),
                entry.getCreatedBy(), postings.stream().map(Line::of).toList());
    }
}
