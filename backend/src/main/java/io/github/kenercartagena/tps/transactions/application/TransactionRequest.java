package io.github.kenercartagena.tps.transactions.application;

import io.github.kenercartagena.tps.shared.money.Money;
import io.github.kenercartagena.tps.transactions.domain.RequestFingerprint;
import io.github.kenercartagena.tps.transactions.domain.TransactionType;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A financial operation to process: what the client asked for.
 *
 * @param sourceAccountId      account debited (money out), if the type has one
 * @param destinationAccountId account credited (money in), if the type has one
 * @param description          optional note of the client, stripped
 */
public record TransactionRequest(
        TransactionType type,
        Money amount,
        @Nullable UUID sourceAccountId,
        @Nullable UUID destinationAccountId,
        @Nullable String description) {

    public TransactionRequest {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(amount, "amount");
        if (type.hasSource() != (sourceAccountId != null)
                || type.hasDestination() != (destinationAccountId != null)) {
            throw new IllegalArgumentException("Wrong accounts for a " + type);
        }
        description = description == null ? null : description.strip();
    }

    /** Money enters the account. */
    public static TransactionRequest deposit(UUID accountId, Money amount, @Nullable String description) {
        return new TransactionRequest(TransactionType.DEPOSIT, amount, null, accountId, description);
    }

    /** Money leaves the account. */
    public static TransactionRequest withdrawal(UUID accountId, Money amount,
            @Nullable String description) {
        return new TransactionRequest(TransactionType.WITHDRAWAL, amount, accountId, null, description);
    }

    /** The customer accounts the operation uses. */
    public Set<UUID> accountIds() {
        Set<UUID> ids = new LinkedHashSet<>();
        if (sourceAccountId != null) {
            ids.add(sourceAccountId);
        }
        if (destinationAccountId != null) {
            ids.add(destinationAccountId);
        }
        return ids;
    }

    /** Identifies what was asked, to tell a retry from a different request with the same key. */
    public RequestFingerprint fingerprint() {
        return RequestFingerprint.of(type, amount.toPlainString(), amount.currency(), sourceAccountId,
                destinationAccountId, description);
    }
}
