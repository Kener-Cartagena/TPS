package io.github.kenercartagena.tps.shared.error;

/**
 * A requested resource (account, transaction, ...) does not exist.
 * Translated into HTTP {@code 404 Not Found}.
 */
public class ResourceNotFoundException extends TpsException {

    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
