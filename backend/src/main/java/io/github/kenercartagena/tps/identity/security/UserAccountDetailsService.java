package io.github.kenercartagena.tps.identity.security;

import io.github.kenercartagena.tps.identity.domain.UserRepository;
import io.github.kenercartagena.tps.identity.domain.Usernames;
import java.time.Clock;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Loads users for the password check at login.
 *
 * <p>Deliberately <em>not</em> a Spring bean: as a bean, Spring Security would also wire
 * it into a global authentication manager (HTTP Basic, form login, ...). TPS checks
 * passwords only in {@link AuthenticationService}.
 */
final class UserAccountDetailsService implements UserDetailsService {

    private final UserRepository users;
    private final Clock clock;

    UserAccountDetailsService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return users.findByUsername(Usernames.normalize(username))
                .map(user -> UserAccountDetails.of(user, clock.instant()))
                // Spring Security turns this into BadCredentialsException ("hide user not
                // found"), so callers cannot tell an unknown user from a wrong password.
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
