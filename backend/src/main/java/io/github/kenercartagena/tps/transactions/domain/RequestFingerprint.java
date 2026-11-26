package io.github.kenercartagena.tps.transactions.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * SHA-256 of the meaning of a request, stored with its transaction. A retry is accepted
 * only if it has the same fingerprint as the first request with the same idempotency key:
 * the same key cannot be reused for a different operation.
 *
 * <p>It is computed from the parsed request, not from the raw JSON, so spacing, field
 * order or {@code "10"} versus {@code "10.00"} do not change it. Each part is written as
 * {@code <length>:<text>} ({@code -} for {@code null}), so no two different lists of parts
 * give the same input: {@code ("ab", "c")} and {@code ("a", "bc")} differ.
 *
 * @param value 64 lowercase hexadecimal characters
 */
public record RequestFingerprint(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[0-9a-f]{64}$");

    public RequestFingerprint {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("A fingerprint is 64 lowercase hexadecimal characters");
        }
    }

    /** The fingerprint of a request made of these parts, in this order. */
    public static RequestFingerprint of(@Nullable Object... parts) {
        StringBuilder canonical = new StringBuilder();
        for (Object part : parts) {
            if (part == null) {
                canonical.append('-');
            } else {
                String text = part.toString();
                canonical.append(text.length()).append(':').append(text);
            }
        }
        return new RequestFingerprint(HexFormat.of().formatHex(sha256(canonical.toString())));
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Every Java runtime must support SHA-256", ex);
        }
    }
}
