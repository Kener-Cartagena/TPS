package io.github.kenercartagena.tps.shared.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
 *
 * <p>An exception may carry extra <em>properties</em>, returned as additional members of
 * the error response, e.g. the id of a rejected transaction so the client can look it
 * up. The same rule applies: never put sensitive data in them.
 */
public abstract class TpsException extends RuntimeException {

    /** Members every error response already has; a property cannot replace them. */
    private static final Set<String> RESERVED_PROPERTIES = Set.of(
            "type", "title", "status", "detail", "instance", "code", "requestId", "timestamp", "errors");

    private final ErrorCode errorCode;
    private final Map<String, Object> properties = new LinkedHashMap<>();

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

    /**
     * Adds a member to the error response, e.g.
     * {@code throw new BusinessRuleViolationException(code, message).withProperty("transactionId", id)}.
     *
     * @param name camelCase name; not one of the standard members ({@code code}, {@code detail}, ...)
     * @return this exception
     */
    public TpsException withProperty(String name, Object value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        if (RESERVED_PROPERTIES.contains(name)) {
            throw new IllegalArgumentException("'" + name + "' is a standard member of error responses");
        }
        properties.put(name, value);
        return this;
    }

    /** The extra members of the error response, in the order they were added. */
    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(properties);
    }
}
