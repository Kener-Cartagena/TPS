package io.github.kenercartagena.tps.shared.error;

/**
 * The caller could not be authenticated with the credentials it presented (e.g. a
 * wrong username or password on login). Translated into HTTP {@code 401 Unauthorized}.
 *
 * <p>The message must not reveal <em>why</em> authentication failed (unknown user,
 * wrong password, locked or disabled account), so it cannot be used to discover
 * which usernames exist.
 */
public class AuthenticationFailedException extends TpsException {

    public AuthenticationFailedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
