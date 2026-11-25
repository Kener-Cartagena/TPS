package io.github.kenercartagena.tps.identity.web;

import io.github.kenercartagena.tps.identity.security.IssuedToken;
import java.time.Instant;

/**
 * Successful login. Field names follow OAuth 2.0 token responses (RFC 6749), in camelCase.
 *
 * @param tokenType always {@code Bearer}: send {@code Authorization: Bearer <accessToken>}
 * @param expiresIn lifetime of the token in seconds
 */
record TokenResponse(String accessToken, String tokenType, long expiresIn, Instant expiresAt) {

    static TokenResponse from(IssuedToken token) {
        return new TokenResponse(token.value(), "Bearer", token.expiresIn(), token.expiresAt());
    }

    /** Never print the token. */
    @Override
    public String toString() {
        return "TokenResponse[accessToken=****, expiresAt=%s]".formatted(expiresAt);
    }
}
