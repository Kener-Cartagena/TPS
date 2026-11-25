package io.github.kenercartagena.tps.identity.security;

import io.github.kenercartagena.tps.identity.domain.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Brute-force protection: after {@code tps.security.login.max-attempts} consecutive failed
 * logins, the account is locked for {@code tps.security.login.lockout}.
 *
 * <p>Each method runs in its own transaction, separate from the login itself: the login
 * fails with an exception, and a shared transaction would roll back the recorded failure
 * together with it.
 */
@Component
class LoginAttemptTracker {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptTracker.class);

    private final UserRepository users;
    private final Clock clock;
    private final int maxAttempts;
    private final Duration lockout;

    LoginAttemptTracker(UserRepository users, Clock clock, SecurityProperties properties) {
        this.users = users;
        this.clock = clock;
        this.maxAttempts = properties.login().maxAttempts();
        this.lockout = properties.login().lockout();
    }

    /** @param username the normalized username; unknown usernames are ignored */
    @Transactional
    public void recordFailure(String username) {
        Instant now = clock.instant();
        if (users.recordFailedLogin(username, maxAttempts, now.plus(lockout)) == 0) {
            return;
        }
        users.findByUsername(username)
                .filter(user -> user.isLocked(now))
                .ifPresent(user -> log.warn("User {} locked until {} after {} failed logins",
                        user.getId(), user.getLockedUntil(), maxAttempts));
    }

    @Transactional
    public void recordSuccess(UUID userId) {
        users.clearFailedLogins(userId);
    }
}
