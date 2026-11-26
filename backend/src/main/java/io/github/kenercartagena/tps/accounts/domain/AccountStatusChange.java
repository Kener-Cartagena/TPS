package io.github.kenercartagena.tps.accounts.domain;

import io.github.kenercartagena.tps.accounts.AccountStatus;
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
 * One entry in the status history of an account: from which status to which, why, by whom
 * and when. The first entry of every account is its opening ({@code fromStatus} is
 * {@code null}). Immutable, in Java and in the database (trigger).
 */
@Entity
@Table(name = "account_status_changes")
@Immutable
public class AccountStatusChange {

    public static final int MAX_REASON_LENGTH = 255;

    static final String OPENING_REASON = "Account opened";

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private @Nullable AccountStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus toStatus;

    @Column(nullable = false, length = MAX_REASON_LENGTH)
    private String reason;

    private @Nullable UUID changedBy;

    @Column(nullable = false)
    private Instant changedAt;

    /** For JPA only. */
    protected AccountStatusChange() {
    }

    private AccountStatusChange(UUID accountId, @Nullable AccountStatus fromStatus,
            AccountStatus toStatus, String reason, @Nullable UUID changedBy, Instant changedAt) {
        Objects.requireNonNull(reason, "reason");
        if (reason.isBlank() || reason.strip().length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("reason must be 1 to 255 characters");
        }
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.fromStatus = fromStatus;
        this.toStatus = Objects.requireNonNull(toStatus, "toStatus");
        this.reason = reason.strip();
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    /** The first history entry of a newly opened (and already saved) account. */
    public static AccountStatusChange opening(Account account, @Nullable UUID openedBy) {
        return new AccountStatusChange(account.getId(), null, account.getStatus(), OPENING_REASON,
                openedBy, account.getOpenedAt());
    }

    static AccountStatusChange of(UUID accountId, AccountStatus fromStatus, AccountStatus toStatus,
            String reason, @Nullable UUID changedBy, Instant changedAt) {
        return new AccountStatusChange(accountId, fromStatus, toStatus, reason, changedBy, changedAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public @Nullable AccountStatus getFromStatus() {
        return fromStatus;
    }

    public AccountStatus getToStatus() {
        return toStatus;
    }

    public String getReason() {
        return reason;
    }

    public @Nullable UUID getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
