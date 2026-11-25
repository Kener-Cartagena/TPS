package io.github.kenercartagena.tps.identity;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The authenticated caller, from the verified access token. Part of the public API of the
 * identity module: other modules use it to record which user performed an operation.
 *
 * <pre>{@code
 * public AccountView open(@AuthenticationPrincipal Jwt token, ...) {
 *     return accounts.open(command, CurrentUser.id(token));
 * }
 * }</pre>
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** The user id: the {@code sub} claim of tokens issued by TPS. */
    public static UUID id(Jwt token) {
        return UUID.fromString(token.getSubject());
    }
}
