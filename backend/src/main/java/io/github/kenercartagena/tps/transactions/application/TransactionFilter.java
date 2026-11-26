package io.github.kenercartagena.tps.transactions.application;

import io.github.kenercartagena.tps.transactions.domain.Transaction;
import io.github.kenercartagena.tps.transactions.domain.TransactionStatus;
import io.github.kenercartagena.tps.transactions.domain.TransactionType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Optional criteria to list transactions; {@code null} means "any". All given criteria
 * must match.
 *
 * @param accountId an account on either side: money in or out of it
 */
public record TransactionFilter(
        @Nullable UUID accountId,
        @Nullable TransactionType type,
        @Nullable TransactionStatus status) {

    public static TransactionFilter none() {
        return new TransactionFilter(null, null, null);
    }

    /** As a JPA query condition. */
    Specification<Transaction> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (accountId != null) {
                predicates.add(cb.or(cb.equal(root.get("sourceAccountId"), accountId),
                        cb.equal(root.get("destinationAccountId"), accountId)));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
