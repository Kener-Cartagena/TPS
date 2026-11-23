package io.github.kenercartagena.tps.shared.error;

/**
 * The request is well-formed but breaks a business rule (insufficient funds,
 * suspended account, invalid state transition, ...).
 * Translated into HTTP {@code 422 Unprocessable Content}.
 */
public class BusinessRuleViolationException extends TpsException {

    public BusinessRuleViolationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public BusinessRuleViolationException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
