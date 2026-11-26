package io.github.kenercartagena.tps.transactions.domain;

import io.github.kenercartagena.tps.shared.money.Currency;
import io.github.kenercartagena.tps.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/**
 * One financial operation and its outcome. The schema is defined by migration {@code V6}.
 *
 * <p>What was requested (type, amount, accounts, idempotency key, fingerprint, who and
 * when) never changes after creation. Only the status moves, through {@link #start},
 * {@link #complete} and {@link #reject}, each following {@link TransactionStatus} and
 * {@link TransactionType#uses}; each returns the history record to store. A transition
 * that breaks those rules is a bug in the engine, never a client error, so it throws
 * {@link IllegalStateException}.
 */
@Entity
@Table(name = "transactions")
@DynamicUpdate
public class Transaction {

    public static final int MAX_DESCRIPTION_LENGTH = 200;
    public static final int MAX_REJECTION_CODE_LENGTH = 50;
    public static final int MAX_REJECTION_DETAIL_LENGTH = 255;

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3, updatable = false)
    private Currency currency;

    @Column(updatable = false)
    private @Nullable UUID sourceAccountId;

    @Column(updatable = false)
    private @Nullable UUID destinationAccountId;

    @Column(length = MAX_DESCRIPTION_LENGTH, updatable = false)
    private @Nullable String description;

    @Column(nullable = false, length = 64, updatable = false)
    private String idempotencyKey;

    @Column(nullable = false, length = 64, updatable = false)
    private String requestHash;

    private @Nullable UUID journalEntryId;

    @Column(length = MAX_REJECTION_CODE_LENGTH)
    private @Nullable String rejectionCode;

    @Column(length = MAX_REJECTION_DETAIL_LENGTH)
    private @Nullable String rejectionDetail;

    @Column(nullable = false, updatable = false)
    private UUID createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private @Nullable Instant processedAt;

    @Version
    private long version;

    /** For JPA only. */
    protected Transaction() {
    }

    private Transaction(TransactionType type, Money amount, @Nullable UUID sourceAccountId,
            @Nullable UUID destinationAccountId, @Nullable String description, IdempotencyKey key,
            RequestFingerprint fingerprint, UUID createdBy, Instant now) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(amount, "amount");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("The amount of a transaction must be positive");
        }
        if (type.hasSource() != (sourceAccountId != null)
                || type.hasDestination() != (destinationAccountId != null)) {
            throw new IllegalArgumentException("Wrong accounts for a " + type);
        }
        if (description != null
                && (description.isBlank() || description.length() > MAX_DESCRIPTION_LENGTH)) {
            throw new IllegalArgumentException("description must be 1 to 200 characters");
        }
        this.type = type;
        this.status = TransactionStatus.CREATED;
        this.amount = amount.amount();
        this.currency = amount.currency();
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.description = description;
        this.idempotencyKey = key.value();
        this.requestHash = fingerprint.value();
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * A new transaction in status {@code CREATED}. Save it, then store
     * {@link TransactionStatusChange#creation} as the first entry of its history.
     */
    public static Transaction create(TransactionType type, Money amount,
            @Nullable UUID sourceAccountId, @Nullable UUID destinationAccountId,
            @Nullable String description, IdempotencyKey key, RequestFingerprint fingerprint,
            UUID createdBy, Instant now) {
        return new Transaction(type, amount, sourceAccountId, destinationAccountId, description,
                key, fingerprint, createdBy, now);
    }

    // ---- Lifecycle -----------------------------------------------------------------

    /** {@code CREATED → PROCESSING}: execution begins. */
    public TransactionStatusChange start(@Nullable UUID actorId, Instant now) {
        return changeTo(TransactionStatus.PROCESSING, null, actorId, now);
    }

    /** {@code PROCESSING → COMPLETED}: the money moved with this journal entry. */
    public TransactionStatusChange complete(UUID journalEntryId, @Nullable UUID actorId, Instant now) {
        Objects.requireNonNull(journalEntryId, "journalEntryId");
        TransactionStatusChange change = changeTo(TransactionStatus.COMPLETED, null, actorId, now);
        this.journalEntryId = journalEntryId;
        this.processedAt = now;
        return change;
    }

    /**
     * {@code → REJECTED}: a business rule refused the operation; no money moved.
     *
     * @param code   error code of the rule, e.g. {@code INSUFFICIENT_FUNDS}
     * @param detail explanation for the client (the error's message)
     */
    public TransactionStatusChange reject(String code, String detail, @Nullable UUID actorId,
            Instant now) {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(detail, "detail");
        if (code.isBlank() || code.length() > MAX_REJECTION_CODE_LENGTH) {
            throw new IllegalArgumentException("A rejection code is 1 to 50 characters");
        }
        TransactionStatusChange change = changeTo(TransactionStatus.REJECTED, code, actorId, now);
        this.rejectionCode = code;
        this.rejectionDetail = truncate(detail, MAX_REJECTION_DETAIL_LENGTH);
        this.processedAt = now;
        return change;
    }

    private TransactionStatusChange changeTo(TransactionStatus target, @Nullable String reason,
            @Nullable UUID actorId, Instant now) {
        if (!status.canChangeTo(target) || !type.uses(target)) {
            throw new IllegalStateException(
                    "A %s cannot change from %s to %s".formatted(type, status, target));
        }
        TransactionStatus previous = status;
        this.status = target;
        this.updatedAt = now;
        return TransactionStatusChange.of(id, previous, target, reason, actorId, now);
    }

    private static String truncate(String text, int maxLength) {
        String stripped = text.strip();
        return stripped.length() <= maxLength ? stripped : stripped.substring(0, maxLength);
    }

    // ---- Accessors -----------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public Money getAmount() {
        return Money.of(amount, currency);
    }

    public Currency getCurrency() {
        return currency;
    }

    public @Nullable UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public @Nullable UUID getDestinationAccountId() {
        return destinationAccountId;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public IdempotencyKey getIdempotencyKey() {
        return new IdempotencyKey(idempotencyKey);
    }

    public RequestFingerprint getRequestFingerprint() {
        return new RequestFingerprint(requestHash);
    }

    public @Nullable UUID getJournalEntryId() {
        return journalEntryId;
    }

    public @Nullable String getRejectionCode() {
        return rejectionCode;
    }

    public @Nullable String getRejectionDetail() {
        return rejectionDetail;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public @Nullable Instant getProcessedAt() {
        return processedAt;
    }
}
