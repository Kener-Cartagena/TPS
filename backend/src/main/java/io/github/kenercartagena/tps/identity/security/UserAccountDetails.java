package io.github.kenercartagena.tps.identity.security;

import io.github.kenercartagena.tps.identity.domain.Role;
import io.github.kenercartagena.tps.identity.domain.User;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * A {@link User} as Spring Security's password check sees it during login. Only used to
 * verify credentials; requests are authenticated with the JWT afterwards.
 */
final class UserAccountDetails implements UserDetails {

    private final UUID userId;
    private final String username;
    private final String passwordHash;
    private final Role role;
    private final boolean enabled;
    private final boolean accountNonLocked;

    private UserAccountDetails(User user, Instant now) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        this.enabled = user.isActive();
        this.accountNonLocked = !user.isLocked(now);
    }

    static UserAccountDetails of(User user, Instant now) {
        return new UserAccountDetails(user, now);
    }

    UUID userId() {
        return userId;
    }

    Role role() {
        return role;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return JwtAuthoritiesConverter.authoritiesOf(role);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    /** Never print the password hash. */
    @Override
    public String toString() {
        return "UserAccountDetails[userId=%s, role=%s]".formatted(userId, role);
    }
}
