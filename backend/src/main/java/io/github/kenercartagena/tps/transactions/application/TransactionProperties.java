package io.github.kenercartagena.tps.transactions.application;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration of the transaction engine ({@code tps.transactions.*}).
 *
 * @param lockTimeout how long an operation waits for a row another operation has locked
 *                    (an account, or a duplicate request with the same idempotency key)
 *                    before giving up with {@code 409 CONCURRENT_MODIFICATION}. Without a
 *                    limit, a stuck operation would make every other request on the same
 *                    account wait forever, each holding a database connection.
 */
@ConfigurationProperties("tps.transactions")
public record TransactionProperties(@DefaultValue("5s") Duration lockTimeout) {

    public TransactionProperties {
        Objects.requireNonNull(lockTimeout, "lockTimeout");
        if (lockTimeout.toMillis() < 1) {
            throw new IllegalArgumentException("tps.transactions.lock-timeout must be at least 1ms");
        }
    }
}
