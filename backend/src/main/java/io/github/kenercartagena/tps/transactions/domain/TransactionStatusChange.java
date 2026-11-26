package io.github.kenercartagena.tps.transactions.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/**
 * One step in the life of a transaction: from which status to which, by whom and when, and
 * for a rejection the code of the rule. The first entry is the creation ({@code fromStatus}
 * is {@code null}). Immutable, in Java and in the database (trigger).
 */
@Entity
@Table(name = "transaction_status_changes")
@Immutable
public class TransactionStatusChange {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private @Nullable TransactionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus toStatus;

    @Column(length = Transaction.MAX_REJECTION_CODE_LENGTH)
    private @Nullable String reason;

    private @Nullable UUID changedBy;

    @Column(nullable = false)
    private Instant changedAt;

    /** Order of the changes; assigned by the database (identity column). */
    @Column(insertable = false, updatable = false)
    private @Nullable Long sequenceNumber;

    /** For JPA only. */
    protected TransactionStatusChange() {
    }

    private TransactionStatusChange(UUID transactionId, @Nullable TransactionStatus fromStatus,
            TransactionStatus toStatus, @Nullable String reason, @Nullable UUID changedBy,
            Instant changedAt) {
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
        this.fromStatus = fromStatus;
        this.toStatus = Objects.requireNonNull(toStatus, "toStatus");
        this.reason = reason;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    /** The first history entry of a newly created (and already saved) transaction. */
    public static TransactionStatusChange creation(Transaction transaction) {
        return new TransactionStatusChange(transaction.getId(), null, transaction.getStatus(), null,
                transaction.getCreatedBy(), transaction.getCreatedAt());
    }

    static TransactionStatusChange of(UUID transactionId, TransactionStatus fromStatus,
            TransactionStatus toStatus, @Nullable String reason, @Nullable UUID changedBy,
            Instant changedAt) {
        return new TransactionStatusChange(transactionId, fromStatus, toStatus, reason, changedBy,
                changedAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public @Nullable TransactionStatus getFromStatus() {
        return fromStatus;
    }

    public TransactionStatus getToStatus() {
        return toStatus;
    }

    public @Nullable String getReason() {
        return reason;
    }

    public @Nullable UUID getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public @Nullable Long getSequenceNumber() {
        return sequenceNumber;
    }
}
