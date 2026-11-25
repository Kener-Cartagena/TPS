package io.github.kenercartagena.tps.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kenercartagena.tps.identity.Permissions;
import io.github.kenercartagena.tps.identity.domain.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/**
 * Issues real tokens and verifies them with the real decoder, without starting Spring:
 * what a token contains, and every way a token must be rejected.
 */
class JwtTokenTests {

    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");
    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);
    private static final UUID USER_ID = UUID.fromString("0193a1b2-0000-7000-8000-000000000001");

    private final JwtConfiguration configuration = new JwtConfiguration();
    private final SecurityProperties properties = properties(SECRET, "tps");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void issuedTokenCarriesUserIdRoleAndExpiration() {
        IssuedToken token = issuer(properties, clock).issue(USER_ID, "jane", Role.AUDITOR);

        Jwt jwt = decoder(properties, clock).decode(token.value());

        assertThat(jwt.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(jwt.getClaimAsString("username")).isEqualTo("jane");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("AUDITOR");
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(jwt.getId()).isNotBlank();
        assertThat(token.expiresIn()).isEqualTo(1800);
    }

    @Test
    void roleIsExpandedIntoItsPermissions() {
        Jwt jwt = decoder(properties, clock)
                .decode(issuer(properties, clock).issue(USER_ID, "admin", Role.SYSTEM_ADMIN).value());

        AbstractAuthenticationToken authentication = new JwtAuthoritiesConverter().convert(jwt);

        assertThat(authentication.getName()).isEqualTo(USER_ID.toString());
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_SYSTEM_ADMIN", Permissions.USER_READ, Permissions.USER_MANAGE);
    }

    @Test
    void expiredTokenIsRejected() {
        String token = issuer(properties, clock).issue(USER_ID, "jane", Role.OPERATOR).value();
        // 30 min lifetime + the 60 s clock skew the validator tolerates.
        Clock later = Clock.offset(clock, Duration.ofMinutes(31).plusSeconds(1));

        assertThatThrownBy(() -> decoder(properties, later).decode(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = issuer(properties, clock).issue(USER_ID, "jane", Role.OPERATOR).value();
        String[] parts = token.split("\\.");
        // Same signature, payload changed to claim the administrator role.
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                new String(Base64.getUrlDecoder().decode(parts[1]))
                        .replace("OPERATOR", "SYSTEM_ADMIN").getBytes());
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThatThrownBy(() -> decoder(properties, clock).decode(forged))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        SecurityProperties other = properties(Base64.getEncoder().encodeToString(otherKey), "tps");
        String token = issuer(other, clock).issue(USER_ID, "jane", Role.OPERATOR).value();

        assertThatThrownBy(() -> decoder(properties, clock).decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        String token = issuer(properties(SECRET, "someone-else"), clock)
                .issue(USER_ID, "jane", Role.OPERATOR).value();

        assertThatThrownBy(() -> decoder(properties, clock).decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenWithoutKnownRoleIsRejected() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256")
                .subject(USER_ID.toString()).claim("role", "SUPERUSER").build();

        assertThatThrownBy(() -> new JwtAuthoritiesConverter().convert(jwt))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void secretShorterThan256BitsStopsTheApplication() {
        SecurityProperties weak = properties(Base64.getEncoder().encodeToString(new byte[16]), "tps");

        assertThatThrownBy(() -> configuration.jwtEncoder(weak))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }

    @Test
    void secretThatIsNotBase64StopsTheApplication() {
        assertThatThrownBy(() -> configuration.jwtEncoder(properties("not base64 !!", "tps")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    private JwtTokenIssuer issuer(SecurityProperties props, Clock issuerClock) {
        return new JwtTokenIssuer(configuration.jwtEncoder(props), props, issuerClock);
    }

    private JwtDecoder decoder(SecurityProperties props, Clock decoderClock) {
        return configuration.jwtDecoder(props, decoderClock);
    }

    private static SecurityProperties properties(String secret, String issuer) {
        return new SecurityProperties(
                new SecurityProperties.Jwt(secret, issuer, Duration.ofMinutes(30)),
                new SecurityProperties.Login(5, Duration.ofMinutes(15)),
                null);
    }
}
