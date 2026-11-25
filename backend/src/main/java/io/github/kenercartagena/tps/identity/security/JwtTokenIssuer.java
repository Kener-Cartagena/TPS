package io.github.kenercartagena.tps.identity.security;

import io.github.kenercartagena.tps.identity.domain.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Issues signed access tokens. Claims:
 *
 * <ul>
 *   <li>{@code sub} — the user id (stable, unlike the username),</li>
 *   <li>{@code username}, {@code role} — the role is expanded into permissions when the
 *       token is verified ({@link JwtAuthoritiesConverter}),</li>
 *   <li>{@code iss}, {@code iat}, {@code exp}, {@code jti} — standard claims.</li>
 * </ul>
 *
 * <p>The token is self-contained: a role or status change takes effect at the next login,
 * at the latest when the current token expires. That is why the lifetime is short.
 */
@Component
class JwtTokenIssuer {

    static final String USERNAME_CLAIM = "username";
    static final String ROLE_CLAIM = "role";

    private final JwtEncoder encoder;
    private final String issuer;
    private final Duration expiration;
    private final Clock clock;

    JwtTokenIssuer(JwtEncoder encoder, SecurityProperties properties, Clock clock) {
        this.encoder = encoder;
        this.issuer = properties.jwt().issuer();
        this.expiration = properties.jwt().expiration();
        this.clock = clock;
    }

    IssuedToken issue(UUID userId, String username, Role role) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(expiration);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim(USERNAME_CLAIM, username)
                .claim(ROLE_CLAIM, role.name())
                .build();
        JwsHeader header = JwsHeader.with(JwtConfiguration.ALGORITHM).type("JWT").build();

        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(value, expiresAt, expiration.toSeconds());
    }
}
