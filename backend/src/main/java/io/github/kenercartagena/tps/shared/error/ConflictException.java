package io.github.kenercartagena.tps.shared.error;

/**
 * The request conflicts with the current state of a resource (a username that is
 * already taken, a concurrent modification, a duplicate operation, ...).
 * Translated into HTTP {@code 409 Conflict}.
 */
public class ConflictException extends TpsException {

    public ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ConflictException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
