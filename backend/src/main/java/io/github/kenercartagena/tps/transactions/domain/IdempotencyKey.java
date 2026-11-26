package io.github.kenercartagena.tps.transactions.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The idempotency key of a request: a value the client generates once per operation (a
 * UUID is recommended) and sends again, unchanged, on every retry of that operation.
 * Sent in the {@value #HEADER} header; unique per user.
 *
 * @param value 1 to 64 letters, digits, {@code .}, {@code _}, {@code :} or {@code -}
 */
public record IdempotencyKey(String value) {

    /** Name of the HTTP request header. */
    public static final String HEADER = "Idempotency-Key";

    /** Same rule as the database constraint {@code ck_transactions_idempotency_key}. */
    public static final String FORMAT = "^[A-Za-z0-9._:-]{1,64}$";

    private static final Pattern PATTERN = Pattern.compile(FORMAT);

    public IdempotencyKey {
        Objects.requireNonNull(value, "value");
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Idempotency keys are 1 to 64 letters, digits, '.', '_', ':' or '-'");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
