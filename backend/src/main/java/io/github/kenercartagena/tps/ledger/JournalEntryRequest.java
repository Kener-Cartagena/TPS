package io.github.kenercartagena.tps.ledger;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A journal entry to post. Whether it is valid (balanced, one currency, open accounts,
 * enough funds) is checked by {@link Ledger#post}.
 *
 * @param description what happened, in words; shown on statements (at most
 *                    {@value #MAX_DESCRIPTION_LENGTH} characters)
 * @param reference   id of the business operation that caused the entry, e.g. a
 *                    transaction id (at most {@value #MAX_REFERENCE_LENGTH} characters),
 *                    or {@code null}
 * @param actorId     user who caused the entry, or {@code null} for the system
 * @param lines       debits and credits, in the order they are recorded
 */
public record JournalEntryRequest(
        String description,
        @Nullable String reference,
        @Nullable UUID actorId,
        List<JournalLine> lines) {

    public static final int MAX_DESCRIPTION_LENGTH = 255;
    public static final int MAX_REFERENCE_LENGTH = 100;

    public JournalEntryRequest {
        Objects.requireNonNull(description, "description");
        if (description.isBlank() || description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException(
                    "description must be 1 to %d characters".formatted(MAX_DESCRIPTION_LENGTH));
        }
        if (reference != null && (reference.isBlank() || reference.length() > MAX_REFERENCE_LENGTH)) {
            throw new IllegalArgumentException(
                    "reference must be 1 to %d characters".formatted(MAX_REFERENCE_LENGTH));
        }
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
    }
}
