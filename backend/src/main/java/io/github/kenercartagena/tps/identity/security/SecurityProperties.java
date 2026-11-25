package io.github.kenercartagena.tps.identity.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Security settings ({@code tps.security.*}). Validated at startup: a missing or invalid
 * value stops the application instead of running insecurely.
 *
 * <p>Property names avoid dashes so their environment variable form is obvious, e.g.
 * {@code tps.security.jwt.secret} ↔ {@code TPS_SECURITY_JWT_SECRET}.
 */
@ConfigurationProperties("tps.security")
@Validated
public record SecurityProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Login login,
        @Valid @Nullable Bootstrap bootstrap) {

    /**
     * @param secret     Base64-encoded HMAC key, at least 256 bits (32 bytes). Never commit a
     *                   real one: provide it with {@code TPS_SECURITY_JWT_SECRET}.
     * @param issuer     value of the {@code iss} claim; tokens from another issuer are rejected
     * @param expiration lifetime of an access token
     */
    public record Jwt(@NotBlank String secret, @NotBlank String issuer, @NotNull Duration expiration) {

        /** Never print the secret. */
        @Override
        public String toString() {
            return "Jwt[secret=****, issuer=%s, expiration=%s]".formatted(issuer, expiration);
        }
    }

    /**
     * @param maxAttempts consecutive failed logins that lock the account
     * @param lockout     how long the account stays locked
     */
    public record Login(@Min(1) int maxAttempts, @NotNull Duration lockout) {
    }

    /** Creates the first administrator when the database has no users at all. */
    public record Bootstrap(@Valid @Nullable Admin admin) {
    }

    /** Leave username or password empty to disable the bootstrap. */
    public record Admin(@Nullable String username, @Nullable String password,
            @Nullable String fullName) {

        boolean isConfigured() {
            return username != null && !username.isBlank() && password != null && !password.isBlank();
        }

        /** Never print the password. */
        @Override
        public String toString() {
            return "Admin[username=%s, password=****, fullName=%s]".formatted(username, fullName);
        }
    }
}
