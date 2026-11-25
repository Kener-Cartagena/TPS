package io.github.kenercartagena.tps.ledger.domain;

import io.github.kenercartagena.tps.ledger.EntryDirection;
import io.github.kenercartagena.tps.ledger.LedgerAccountType;
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
import java.util.regex.Pattern;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/**
 * An account of the general ledger. The schema is defined by migration {@code V3}.
 *
 * <p>The balance is stored on the account's normal side (see {@link LedgerAccountType}) and
 * only changes through {@link JournalEntry#post}, together with the postings that explain
 * it. The database rejects a negative balance when {@code allowNegative} is false.
 */
@Entity
@Table(name = "ledger_accounts")
@DynamicUpdate
public class LedgerAccount {

    /** Same rule as the database constraint {@code ck_ledger_accounts_code_format}. */
    public static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9-]{3,50}$");

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, length = 50, updatable = false)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private LedgerAccountType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3, updatable = false)
    private Currency currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false, updatable = false)
    private boolean allowNegative;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerAccountStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private @Nullable Instant closedAt;

    @Version
    private long version;

    /** For JPA only. */
    protected LedgerAccount() {
    }

    private LedgerAccount(String code, String name, LedgerAccountType type, Currency currency,
            boolean allowNegative, Instant now) {
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException(
                    "Ledger account codes are 3 to 50 uppercase letters, digits or '-'");
        }
        if (name.isBlank() || name.length() > 100) {
            throw new IllegalArgumentException("Ledger account names are 1 to 100 characters");
        }
        this.code = code;
        this.name = name.strip();
        this.type = Objects.requireNonNull(type, "type");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.balance = Money.zero(currency).amount();
        this.allowNegative = allowNegative;
        this.status = LedgerAccountStatus.OPEN;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** A new, open account with a zero balance. */
    public static LedgerAccount open(String code, String name, LedgerAccountType type,
            Currency currency, boolean allowNegative, Instant now) {
        return new LedgerAccount(code, name, type, currency, allowNegative, now);
    }

    // ---- Behavior ------------------------------------------------------------------

    public boolean isOpen() {
        return status == LedgerAccountStatus.OPEN;
    }

    /** Whether the account may end up with this balance. */
    public boolean allowsBalance(Money newBalance) {
        return allowNegative || !newBalance.isNegative();
    }

    /**
     * The change a posting makes to this account's balance: positive on its normal side,
     * negative on the other.
     */
    public Money effectOf(EntryDirection direction, Money amount) {
        return direction == type.normalSide() ? amount : amount.negate();
    }

    /**
     * Closes the account if its balance is zero.
     *
     * @return {@code true} if the account is closed (now or before)
     */
    public boolean closeIfEmpty(Instant now) {
        if (!isOpen()) {
            return true;
        }
        if (!getBalance().isZero()) {
            return false;
        }
        this.status = LedgerAccountStatus.CLOSED;
        this.closedAt = now;
        this.updatedAt = now;
        return true;
    }

    /** Only {@link JournalEntry#post}, after every rule has been checked. */
    void applyBalance(Money newBalance, Instant now) {
        if (newBalance.currency() != currency || !allowsBalance(newBalance)) {
            throw new IllegalStateException("Balance rules must be checked before applying");
        }
        this.balance = newBalance.amount();
        this.updatedAt = now;
    }

    // ---- Accessors -----------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public LedgerAccountType getType() {
        return type;
    }

    public Currency getCurrency() {
        return currency;
    }

    public Money getBalance() {
        return Money.of(balance, currency);
    }

    public boolean isNegativeBalanceAllowed() {
        return allowNegative;
    }

    public LedgerAccountStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public @Nullable Instant getClosedAt() {
        return closedAt;
    }
}
