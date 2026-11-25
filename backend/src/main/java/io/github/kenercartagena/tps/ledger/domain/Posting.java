package io.github.kenercartagena.tps.ledger.domain;

import io.github.kenercartagena.tps.ledger.EntryDirection;
import io.github.kenercartagena.tps.shared.money.Currency;
import io.github.kenercartagena.tps.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/**
 * One line of a {@link JournalEntry}: an amount debited or credited to a ledger account,
 * with the account's balance right after it. Immutable, in Java ({@code @Immutable}) and
 * in the database (trigger). Created only by {@link JournalEntry#post}.
 */
@Entity
@Table(name = "ledger_postings")
@Immutable
public class Posting {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private JournalEntry journalEntry;

    @Column(nullable = false)
    private int lineNumber;

    @Column(nullable = false)
    private UUID ledgerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private EntryDirection direction;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(nullable = false)
    private Instant postedAt;

    /** Assigned by the database (identity column); {@code null} until loaded again. */
    @Column(insertable = false, updatable = false)
    private @Nullable Long sequenceNumber;

    /** For JPA only. */
    protected Posting() {
    }

    Posting(JournalEntry journalEntry, int lineNumber, UUID ledgerAccountId,
            EntryDirection direction, Money amount, Money balanceAfter, Instant postedAt) {
        this.journalEntry = journalEntry;
        this.lineNumber = lineNumber;
        this.ledgerAccountId = ledgerAccountId;
        this.currency = amount.currency();
        this.direction = direction;
        this.amount = amount.amount();
        this.balanceAfter = balanceAfter.amount();
        this.postedAt = postedAt;
    }

    public UUID getId() {
        return id;
    }

    public JournalEntry getJournalEntry() {
        return journalEntry;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public UUID getLedgerAccountId() {
        return ledgerAccountId;
    }

    public EntryDirection getDirection() {
        return direction;
    }

    public Money getAmount() {
        return Money.of(amount, currency);
    }

    public Money getBalanceAfter() {
        return Money.of(balanceAfter, currency);
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public @Nullable Long getSequenceNumber() {
        return sequenceNumber;
    }
}
