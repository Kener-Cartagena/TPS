package io.github.kenercartagena.tps.shared.internal;

/**
 * One validation failure in an error response's {@code errors} list.
 *
 * <p>The rejected value is intentionally not included, so sensitive input (passwords,
 * account numbers) is never echoed back to the client.
 *
 * @param field   the request field or parameter that failed validation
 * @param message why the value was rejected
 */
record FieldViolation(String field, String message) {
}
