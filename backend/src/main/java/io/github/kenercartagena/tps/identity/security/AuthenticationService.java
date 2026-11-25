package io.github.kenercartagena.tps.identity.security;

import io.github.kenercartagena.tps.identity.application.IdentityErrorCode;
import io.github.kenercartagena.tps.identity.domain.UserRepository;
import io.github.kenercartagena.tps.identity.domain.Usernames;
import io.github.kenercartagena.tps.shared.error.AuthenticationFailedException;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Login: verifies a username and password and issues an access token.
 *
 * <p>The password check is Spring Security's {@link DaoAuthenticationProvider}: it compares
 * hashes, rejects locked and disabled accounts, and spends the same hashing time when the
 * user does not exist, so response times do not reveal valid usernames.
 *
 * <p>Every failure returns the same {@code 401 INVALID_CREDENTIALS}, whatever the reason
 * (unknown user, wrong password, locked, disabled). The reason is only logged.
 *
 * <p>Not {@code @Transactional} on purpose: see {@link LoginAttemptTracker}.
 */
@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private final AuthenticationManager passwordAuthentication;
    private final LoginAttemptTracker loginAttempts;
    private final JwtTokenIssuer tokenIssuer;

    AuthenticationService(UserRepository users, PasswordEncoder passwordEncoder, Clock clock,
            LoginAttemptTracker loginAttempts, JwtTokenIssuer tokenIssuer) {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(new UserAccountDetailsService(users, clock));
        provider.setPasswordEncoder(passwordEncoder);
        this.passwordAuthentication = new ProviderManager(provider);
        this.loginAttempts = loginAttempts;
        this.tokenIssuer = tokenIssuer;
    }

    /** @throws AuthenticationFailedException with {@code INVALID_CREDENTIALS} */
    public IssuedToken login(String username, String password) {
        String normalized = Usernames.normalize(username);
        UserAccountDetails user;
        try {
            user = (UserAccountDetails) passwordAuthentication
                    .authenticate(UsernamePasswordAuthenticationToken.unauthenticated(normalized, password))
                    .getPrincipal();
        } catch (BadCredentialsException ex) {
            loginAttempts.recordFailure(normalized);
            log.info("Login failed: bad credentials");
            throw invalidCredentials();
        } catch (AuthenticationException ex) {
            // LockedException, DisabledException, ...
            log.info("Login rejected: {}", ex.getClass().getSimpleName());
            throw invalidCredentials();
        }

        loginAttempts.recordSuccess(user.userId());
        log.info("User {} logged in", user.userId());
        return tokenIssuer.issue(user.userId(), user.getUsername(), user.role());
    }

    private static AuthenticationFailedException invalidCredentials() {
        return new AuthenticationFailedException(IdentityErrorCode.INVALID_CREDENTIALS,
                "Invalid username or password.");
    }
}
