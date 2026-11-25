package io.github.kenercartagena.tps.identity.security;

import java.time.Instant;

/**
 * An access token returned by a successful login.
 *
 * @param value     the encoded JWT
 * @param expiresAt when the token stops being accepted
 * @param expiresIn lifetime in seconds, as in OAuth 2.0 token responses
 */
public record IssuedToken(String value, Instant expiresAt, long expiresIn) {

    /** Never print the token: it is a credential. */
    @Override
    public String toString() {
        return "IssuedToken[value=****, expiresAt=%s]".formatted(expiresAt);
    }
}
