package io.github.kenercartagena.tps.identity.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** @param username the normalized username (see {@link Usernames#normalize}) */
    Optional<User> findByUsername(String username);

    /** @param username the normalized username (see {@link Usernames#normalize}) */
    boolean existsByUsername(String username);

    /**
     * Users with the given role and status, locked with {@code SELECT ... FOR UPDATE}
     * until the transaction ends. Used to check "at least one active administrator
     * remains": two administrators demoting each other at the same time are serialized
     * instead of both succeeding.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = :role and u.status = :status")
    List<User> lockAllByRoleAndStatus(@Param("role") Role role, @Param("status") UserStatus status);

    /**
     * Records a failed login in one atomic statement, so concurrent attempts cannot lose
     * an increment. When the count reaches {@code maxAttempts}, the account is locked
     * until {@code lockedUntil} and the counter restarts (a fresh set of attempts after
     * the lockout expires). In SQL, every {@code SET} expression reads the old values.
     *
     * @return the number of users updated (0 if the username does not exist)
     */
    @Modifying
    @Query("""
            update User u set
                u.failedLoginAttempts = case when u.failedLoginAttempts + 1 >= :maxAttempts
                                             then 0 else u.failedLoginAttempts + 1 end,
                u.lockedUntil = case when u.failedLoginAttempts + 1 >= :maxAttempts
                                     then :lockedUntil else u.lockedUntil end
            where u.username = :username""")
    int recordFailedLogin(@Param("username") String username,
            @Param("maxAttempts") int maxAttempts, @Param("lockedUntil") Instant lockedUntil);

    /** Clears failed attempts and any lockout after a successful login; no-op if clean. */
    @Modifying
    @Query("""
            update User u set u.failedLoginAttempts = 0, u.lockedUntil = null
            where u.id = :id and (u.failedLoginAttempts > 0 or u.lockedUntil is not null)""")
    int clearFailedLogins(@Param("id") UUID id);
}
