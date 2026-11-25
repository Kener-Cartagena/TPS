package io.github.kenercartagena.tps.accounts.domain;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/** Status history is only added and read: no update or delete methods. */
public interface AccountStatusChangeRepository extends Repository<AccountStatusChange, UUID> {

    AccountStatusChange save(AccountStatusChange change);

    Page<AccountStatusChange> findByAccountId(UUID accountId, Pageable pageable);
}
