package io.github.kenercartagena.tps.transactions.application;

import io.github.kenercartagena.tps.accounts.OperableAccount;
import io.github.kenercartagena.tps.ledger.EntryDirection;
import io.github.kenercartagena.tps.ledger.JournalEntryRequest;
import io.github.kenercartagena.tps.ledger.JournalLine;
import io.github.kenercartagena.tps.shared.money.Money;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * How a transaction moves its amount: which ledger accounts are debited and credited, and
 * which of them are customer accounts (whose status and funds the engine checks). Built by
 * the {@link OperationHandler} of the transaction's type.
 *
 * @param description of the journal entry, shown on statements
 * @param legs        one per ledger account; every leg moves the transaction's amount
 */
record MoneyMovement(String description, List<Leg> legs) {

    MoneyMovement {
        Objects.requireNonNull(description, "description");
        legs = List.copyOf(legs);
    }

    /**
     * One side of the movement.
     *
     * @param customerAccount the customer account behind the ledger account, or
     *                        {@code null} for an internal account (e.g. settlement)
     */
    record Leg(UUID ledgerAccountId, EntryDirection direction, @Nullable OperableAccount customerAccount) {

        static Leg customer(OperableAccount account, EntryDirection direction) {
            return new Leg(account.ledgerAccountId(), direction, account);
        }

        static Leg internal(UUID ledgerAccountId, EntryDirection direction) {
            return new Leg(ledgerAccountId, direction, null);
        }
    }

    /** The ledger accounts to lock before checking the rules. */
    Set<UUID> ledgerAccountIds() {
        Set<UUID> ids = new LinkedHashSet<>();
        legs.forEach(leg -> ids.add(leg.ledgerAccountId()));
        return ids;
    }

    /** The journal entry that executes the movement. */
    JournalEntryRequest toJournalEntry(Money amount, String reference, @Nullable UUID actorId) {
        List<JournalLine> lines = legs.stream()
                .map(leg -> new JournalLine(leg.ledgerAccountId(), leg.direction(), amount))
                .toList();
        return new JournalEntryRequest(description, reference, actorId, lines);
    }
}
