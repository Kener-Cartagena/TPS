package io.github.kenercartagena.tps.accounts.application;

import io.github.kenercartagena.tps.accounts.domain.Account;
import io.github.kenercartagena.tps.accounts.domain.AccountStatus;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Optional criteria to list accounts; {@code null} means "any". All given criteria must match.
 *
 * @param accountNumber exact 12-digit number
 */
public record AccountFilter(
        @Nullable UUID holderId,
        @Nullable AccountStatus status,
        @Nullable String accountNumber) {

    public static AccountFilter none() {
        return new AccountFilter(null, null, null);
    }

    /** As a JPA query condition. */
    Specification<Account> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (holderId != null) {
                predicates.add(cb.equal(root.get("holderId"), holderId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (accountNumber != null) {
                predicates.add(cb.equal(root.get("accountNumber"), accountNumber));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
