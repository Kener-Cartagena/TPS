package io.github.kenercartagena.tps.accounts.domain;

import io.github.kenercartagena.tps.accounts.AccountStatus;
import io.github.kenercartagena.tps.shared.error.BusinessRuleViolationException;
import io.github.kenercartagena.tps.shared.money.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/**
 * A customer account. The schema is defined by migration {@code V5}.
 *
 * <p>The balance is not stored here: it lives in the account's ledger account
 * ({@link #getLedgerAccountId()}), next to the postings that explain it. Number, holder,
 * type and currency never change; the status changes through {@link #changeStatus}, which
 * returns the history record to store.
 */
@Entity
@Table(name = "accounts")
@DynamicUpdate
public class Account {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, length = 12, updatable = false)
    private String accountNumber;

    @Column(nullable = false, updatable = false)
    private UUID holderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private AccountType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3, updatable = false)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(nullable = false, updatable = false)
    private UUID ledgerAccountId;

    @Column(updatable = false)
    private @Nullable UUID createdBy;

    @Column(nullable = false, updatable = false)
    private Instant openedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private @Nullable Instant closedAt;

    @Version
    private long version;

    /** For JPA only. */
    protected Account() {
    }

    private Account(AccountNumber accountNumber, UUID holderId, AccountType type, Currency currency,
            UUID ledgerAccountId, @Nullable UUID createdBy, Instant now) {
        this.accountNumber = accountNumber.value();
        this.holderId = Objects.requireNonNull(holderId, "holderId");
        this.type = Objects.requireNonNull(type, "type");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.ledgerAccountId = Objects.requireNonNull(ledgerAccountId, "ledgerAccountId");
        this.status = AccountStatus.ACTIVE;
        this.createdBy = createdBy;
        this.openedAt = now;
        this.updatedAt = now;
    }

    /** A new, active account backed by an already opened ledger account. */
    public static Account open(AccountNumber accountNumber, UUID holderId, AccountType type,
            Currency currency, UUID ledgerAccountId, @Nullable UUID createdBy, Instant now) {
        return new Account(accountNumber, holderId, type, currency, ledgerAccountId, createdBy, now);
    }

    // ---- Behavior ------------------------------------------------------------------

    /**
     * @throws BusinessRuleViolationException {@code INVALID_STATUS_TRANSITION} if the
     *         account cannot change from its current status to the target
     */
    public void ensureCanChangeTo(AccountStatus target) {
        if (!status.canChangeTo(target)) {
            throw new BusinessRuleViolationException(AccountsErrorCode.INVALID_STATUS_TRANSITION,
                    "An account in status %s cannot change to %s.".formatted(status, target));
        }
    }

    /**
     * Changes the status. Closing requires a zero balance, which the caller checks with the
     * ledger first.
     *
     * @return the history record of the change, to be stored
     */
    public AccountStatusChange changeStatus(AccountStatus target, String reason,
            @Nullable UUID changedBy, Instant now) {
        ensureCanChangeTo(target);
        AccountStatus previous = status;
        this.status = target;
        this.updatedAt = now;
        if (target == AccountStatus.CLOSED) {
            this.closedAt = now;
        }
        return AccountStatusChange.of(id, previous, target, reason, changedBy, now);
    }

    // ---- Accessors -----------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public AccountNumber getAccountNumber() {
        return new AccountNumber(accountNumber);
    }

    public UUID getHolderId() {
        return holderId;
    }

    public AccountType getType() {
        return type;
    }

    public Currency getCurrency() {
        return currency;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public UUID getLedgerAccountId() {
        return ledgerAccountId;
    }

    public @Nullable UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public @Nullable Instant getClosedAt() {
        return closedAt;
    }
}
