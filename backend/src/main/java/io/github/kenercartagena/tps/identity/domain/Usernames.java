package io.github.kenercartagena.tps.identity.domain;

import java.util.Locale;

/**
 * Username rules. Usernames are case-insensitive: they are stored and compared in their
 * normalized form (trimmed, lowercase), so {@code "Admin"} and {@code "admin"} are the
 * same user.
 */
public final class Usernames {

    /**
     * Allowed characters and length, applied to the value the client sends (before
     * normalization). The database enforces the same rule on the normalized value.
     */
    public static final String PATTERN = "^[A-Za-z0-9._-]{3,50}$";

    private Usernames() {
    }

    public static String normalize(String username) {
        return username.strip().toLowerCase(Locale.ROOT);
    }
}
