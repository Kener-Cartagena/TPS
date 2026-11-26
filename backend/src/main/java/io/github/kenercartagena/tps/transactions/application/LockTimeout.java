package io.github.kenercartagena.tps.transactions.application;

import jakarta.persistence.EntityManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Limits how long the current database transaction waits for a lock, with PostgreSQL's
 * {@code lock_timeout}. {@code set_config(..., true)} is {@code SET LOCAL}: it ends with
 * the transaction, so connections go back to the pool unchanged.
 */
@Component
@EnableConfigurationProperties(TransactionProperties.class)
class LockTimeout {

    private final EntityManager entityManager;
    private final String timeout;

    LockTimeout(EntityManager entityManager, TransactionProperties properties) {
        this.entityManager = entityManager;
        this.timeout = properties.lockTimeout().toMillis() + "ms";
    }

    /** Must be called inside the transaction it applies to, before taking any lock. */
    void applyToCurrentTransaction() {
        entityManager.createNativeQuery("select set_config('lock_timeout', :timeout, true)")
                .setParameter("timeout", timeout)
                .getSingleResult();
    }
}
