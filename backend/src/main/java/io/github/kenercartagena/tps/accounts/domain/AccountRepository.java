package io.github.kenercartagena.tps.accounts.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID>, JpaSpecificationExecutor<Account> {

    /**
     * The account, locked with {@code SELECT ... FOR UPDATE} until the transaction ends, so
     * two status changes of the same account are applied one after the other. Use it as
     * the first read of the account in a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> lockById(@Param("id") UUID id);

    /**
     * The account, locked with {@code SELECT ... FOR SHARE} until the transaction ends.
     * Many money movements can hold this lock on the same account at the same time, but a
     * status change ({@link #lockById}) waits until they all finish, and they wait for it:
     * the status cannot change while money is moving. Use it as the first read of the
     * account in a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> lockForShareById(@Param("id") UUID id);

    /** The next value of the account number sequence (never repeats; gaps are normal). */
    @Query(value = "select nextval('account_number_seq')", nativeQuery = true)
    long nextAccountNumberSequence();
}
