package io.github.kenercartagena.tps.identity.application;

import io.github.kenercartagena.tps.shared.error.BusinessRuleViolationException;
import java.nio.charset.StandardCharsets;

/**
 * Password rules, following NIST SP 800-63B: length matters, composition rules
 * ("one uppercase, one symbol, ...") do not, because they push people towards
 * predictable passwords.
 *
 * <ul>
 *   <li>{@value #MIN_LENGTH} to {@value #MAX_LENGTH} characters; any character, including
 *       spaces, is allowed (passphrases are welcome).</li>
 *   <li>At most {@value #MAX_BYTES} bytes in UTF-8: bcrypt only uses the first 72
 *       bytes, so a longer password would be silently truncated.</li>
 *   <li>Must not be the username.</li>
 * </ul>
 *
 * <p>The web layer validates the length with Bean Validation for a fast {@code 400};
 * this class is the authoritative check and also covers passwords that do not come
 * from a request (the bootstrap administrator).
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 64;
    static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    /** @throws BusinessRuleViolationException with {@code PASSWORD_POLICY_VIOLATION} */
    public static void check(String password, String username) {
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw violation("The password must be between %d and %d characters long."
                    .formatted(MIN_LENGTH, MAX_LENGTH));
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw violation("The password is too long: use fewer special characters or emoji.");
        }
        if (password.strip().equalsIgnoreCase(username.strip())) {
            throw violation("The password must not be the same as the username.");
        }
    }

    private static BusinessRuleViolationException violation(String message) {
        return new BusinessRuleViolationException(IdentityErrorCode.PASSWORD_POLICY_VIOLATION, message);
    }
}
