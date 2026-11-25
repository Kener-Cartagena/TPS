package io.github.kenercartagena.tps.ledger.domain;

import io.github.kenercartagena.tps.ledger.JournalEntryRequest;
import io.github.kenercartagena.tps.ledger.JournalLine;
import io.github.kenercartagena.tps.shared.error.BusinessRuleViolationException;
import io.github.kenercartagena.tps.shared.money.Currency;
import io.github.kenercartagena.tps.shared.money.Money;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/**
 * One financial event of the general ledger, recorded as balanced debit and credit
 * {@link Posting postings}. Immutable: an entry is never changed or deleted; a mistake is
 * corrected with another entry. The schema is defined by migration {@code V3}.
 *
 * <p>{@link #post} holds the bookkeeping rules. It is plain Java (no database), so every
 * rule is unit-tested; the database enforces the same rules again.
 */
@Entity
@Table(name = "journal_entries")
@Immutable
public class JournalEntry {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, length = JournalEntryRequest.MAX_DESCRIPTION_LENGTH)
    private String description;

    @Column(length = JournalEntryRequest.MAX_REFERENCE_LENGTH)
    private @Nullable String reference;

    @Column(nullable = false)
    private Instant postedAt;

    private @Nullable UUID createdBy;

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.PERSIST)
    @OrderBy("lineNumber")
    private List<Posting> postings = new ArrayList<>();

    /** For JPA only. */
    protected JournalEntry() {
    }

    private JournalEntry(JournalEntryRequest request, Instant now) {
        this.description = request.description().strip();
        this.reference = request.reference() == null ? null : request.reference().strip();
        this.createdBy = request.actorId();
        this.postedAt = now;
    }

    /**
     * Builds the entry and applies it to the balances of its accounts.
     *
     * <p>Every rule is checked <em>before</em> any balance changes, so a rejected entry
     * leaves the accounts untouched:
     * <ol>
     *   <li>at least two lines, all with positive amounts in one currency,</li>
     *   <li>total debits equal total credits,</li>
     *   <li>every account is open and in the entry's currency,</li>
     *   <li>no account that forbids it ends with a negative balance.</li>
     * </ol>
     * An account may appear in several lines; each posting records the running balance.
     *
     * @param accounts every account used by the lines, by id, locked by the caller so their
     *                 balances cannot change until the transaction ends
     */
    public static JournalEntry post(JournalEntryRequest request, Map<UUID, LedgerAccount> accounts,
            Instant now) {
        Currency currency = checkStructure(request.lines());
        for (JournalLine line : request.lines()) {
            LedgerAccount account = accountOf(line, accounts);
            if (!account.isOpen()) {
                throw rejected(LedgerErrorCode.LEDGER_ACCOUNT_CLOSED,
                        "A journal entry cannot be posted to a closed ledger account.");
            }
            if (account.getCurrency() != currency) {
                throw rejected(LedgerErrorCode.CURRENCY_MISMATCH,
                        "Every line must use the currency of its ledger account.");
            }
        }

        JournalEntry entry = new JournalEntry(request, now);
        Map<UUID, Money> balances = new LinkedHashMap<>();
        int lineNumber = 1;
        for (JournalLine line : request.lines()) {
            LedgerAccount account = accountOf(line, accounts);
            Money before = balances.getOrDefault(line.ledgerAccountId(), account.getBalance());
            Money after = before.plus(account.effectOf(line.direction(), line.amount()));
            balances.put(line.ledgerAccountId(), after);
            entry.postings.add(new Posting(entry, lineNumber++, line.ledgerAccountId(),
                    line.direction(), line.amount(), after, now));
        }

        balances.forEach((accountId, finalBalance) -> {
            if (!accounts.get(accountId).allowsBalance(finalBalance)) {
                throw rejected(LedgerErrorCode.INSUFFICIENT_FUNDS,
                        "The account does not have enough funds for this operation.");
            }
        });
        balances.forEach((accountId, finalBalance) ->
                accounts.get(accountId).applyBalance(finalBalance, now));
        return entry;
    }

    /** Rules 1 and 2. @return the currency of the entry */
    private static Currency checkStructure(List<JournalLine> lines) {
        if (lines.size() < 2) {
            throw rejected(LedgerErrorCode.INVALID_JOURNAL_ENTRY,
                    "A journal entry needs at least two lines.");
        }
        Currency currency = lines.getFirst().amount().currency();
        Money debits = Money.zero(currency);
        Money credits = Money.zero(currency);
        for (JournalLine line : lines) {
            if (line.amount().currency() != currency) {
                throw rejected(LedgerErrorCode.INVALID_JOURNAL_ENTRY,
                        "All lines of a journal entry must use the same currency.");
            }
            if (!line.amount().isPositive()) {
                throw rejected(LedgerErrorCode.INVALID_JOURNAL_ENTRY,
                        "Every line of a journal entry must have a positive amount.");
            }
            switch (line.direction()) {
                case DEBIT -> debits = debits.plus(line.amount());
                case CREDIT -> credits = credits.plus(line.amount());
            }
        }
        if (!debits.equals(credits)) {
            throw rejected(LedgerErrorCode.UNBALANCED_JOURNAL_ENTRY,
                    "Total debits (%s) must equal total credits (%s).".formatted(debits, credits));
        }
        return currency;
    }

    private static LedgerAccount accountOf(JournalLine line, Map<UUID, LedgerAccount> accounts) {
        LedgerAccount account = accounts.get(line.ledgerAccountId());
        if (account == null) {
            throw new IllegalArgumentException("Account %s was not loaded".formatted(line.ledgerAccountId()));
        }
        return account;
    }

    private static BusinessRuleViolationException rejected(LedgerErrorCode code, String message) {
        return new BusinessRuleViolationException(code, message);
    }

    // ---- Accessors -----------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public @Nullable String getReference() {
        return reference;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public @Nullable UUID getCreatedBy() {
        return createdBy;
    }

    /** The lines, in order. */
    public List<Posting> getPostings() {
        return Collections.unmodifiableList(postings);
    }
}
