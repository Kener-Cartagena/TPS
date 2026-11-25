package io.github.kenercartagena.tps.identity.web;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** The authenticated caller, from the verified access token. */
final class CurrentUser {

    private CurrentUser() {
    }

    /** The user id: the {@code sub} claim of tokens issued by TPS. */
    static UUID id(Jwt token) {
        return UUID.fromString(token.getSubject());
    }
}
