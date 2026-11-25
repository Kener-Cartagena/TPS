package io.github.kenercartagena.tps.ledger;

import java.time.Instant;
import java.util.UUID;

/** Result of posting a journal entry. */
public record PostedJournalEntry(UUID journalEntryId, Instant postedAt) {
}
