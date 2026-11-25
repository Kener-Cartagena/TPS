package io.github.kenercartagena.tps.identity.security;

import java.time.Clock;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Signing and verification of access tokens (JWT, HMAC-SHA256), see ADR 0005.
 *
 * <p>TPS both issues and verifies its tokens, so one shared secret is enough (HS256).
 * Asymmetric keys (RS256) become worthwhile when another service must verify tokens
 * without being able to issue them.
 *
 * <p>The decoder rejects tokens that are tampered with, signed with another key or
 * algorithm, expired or not yet valid (60 s clock skew), or from another issuer.
 */
@Configuration(proxyBeanMethods = false)
class JwtConfiguration {

    static final MacAlgorithm ALGORITHM = MacAlgorithm.HS256;

    /** HS256 requires a key of at least 256 bits. */
    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    JwtEncoder jwtEncoder(SecurityProperties properties) {
        return NimbusJwtEncoder.withSecretKey(secretKey(properties)).algorithm(ALGORITHM).build();
    }

    @Bean
    JwtDecoder jwtDecoder(SecurityProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(ALGORITHM)
                .build();
        decoder.setJwtValidator(validator(properties.jwt().issuer(), clock));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> validator(String issuer, Clock clock) {
        JwtTimestampValidator timestamps = new JwtTimestampValidator();
        timestamps.setClock(clock);
        return new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(issuer));
    }

    static SecretKey secretKey(SecurityProperties properties) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(properties.jwt().secret().strip());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("tps.security.jwt.secret must be Base64-encoded", ex);
        }
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(("tps.security.jwt.secret must decode to at least %d "
                    + "bytes (256 bits); generate one with: openssl rand -base64 32")
                    .formatted(MIN_SECRET_BYTES));
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
