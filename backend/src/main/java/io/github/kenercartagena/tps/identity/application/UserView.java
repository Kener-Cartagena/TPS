package io.github.kenercartagena.tps.identity.application;

import io.github.kenercartagena.tps.identity.domain.Role;
import io.github.kenercartagena.tps.identity.domain.User;
import io.github.kenercartagena.tps.identity.domain.UserStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Read model of a user, returned by the API. Never contains the password hash or the
 * failed login counter.
 *
 * @param locked temporarily locked after too many failed logins
 */
public record UserView(
        UUID id,
        String username,
        String fullName,
        Role role,
        UserStatus status,
        boolean locked,
        Instant createdAt,
        Instant updatedAt) {

    static UserView of(User user, Instant now) {
        return new UserView(user.getId(), user.getUsername(), user.getFullName(), user.getRole(),
                user.getStatus(), user.isLocked(now), user.getCreatedAt(), user.getUpdatedAt());
    }
}
