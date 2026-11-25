package io.github.kenercartagena.tps.shared.error;

import java.util.Objects;

/**
 * Base class of every expected (business) failure in TPS.
 *
 * <p>It cannot be extended directly: modules use one of its categories —
 * {@link ResourceNotFoundException} (404), {@link ConflictException} (409),
 * {@link BusinessRuleViolationException} (422) or {@link AuthenticationFailedException}
 * (401) — so every failure maps to a well-defined HTTP status.
 *
 * <p><strong>The message is returned to API clients</strong> as the {@code detail} of
 * the error response. It must be clear and must never contain sensitive data
 * (credentials, full account numbers, internal identifiers of other customers, ...).
 */
public abstract class TpsException extends RuntimeException {

    private final ErrorCode errorCode;

    TpsException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    TpsException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
