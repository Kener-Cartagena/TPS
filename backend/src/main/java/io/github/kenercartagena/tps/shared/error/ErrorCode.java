package io.github.kenercartagena.tps.shared.error;

/**
 * Stable, machine-readable identifier of an error, returned to API clients in the
 * {@code code} property of every error response.
 *
 * <p>Each module defines its own codes, typically as an enum, e.g.:
 *
 * <pre>{@code
 * enum AccountErrorCode implements ErrorCode {
 *     ACCOUNT_NOT_FOUND;
 *
 *     public String code() { return name(); }
 * }
 * }</pre>
 *
 * <p>Codes are part of the public API contract: once released, never rename them.
 * Use {@code UPPER_SNAKE_CASE}.
 */
public interface ErrorCode {

    String code();
}
