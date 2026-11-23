package io.github.kenercartagena.tps.shared.error;

/**
 * Generic error codes for failures that are not specific to a business module
 * (invalid requests, unknown endpoints, unexpected errors).
 */
public enum CommonErrorCode implements ErrorCode {

    /** The request body or parameters failed Bean Validation. */
    VALIDATION_FAILED,
    /** The request body could not be read (malformed JSON, wrong types). */
    MALFORMED_REQUEST,
    /** Any other client error not covered by a more specific code. */
    BAD_REQUEST,
    /** The requested endpoint or resource does not exist. */
    NOT_FOUND,
    METHOD_NOT_ALLOWED,
    NOT_ACCEPTABLE,
    UNSUPPORTED_MEDIA_TYPE,
    /** An unexpected server-side failure. Details are logged, never returned. */
    INTERNAL_ERROR,
    /** Fallback for any other HTTP error status. */
    REQUEST_FAILED;

    @Override
    public String code() {
        return name();
    }
}
