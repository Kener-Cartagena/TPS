package io.github.kenercartagena.tps.shared.internal;

import io.github.kenercartagena.tps.shared.error.AuthenticationFailedException;
import io.github.kenercartagena.tps.shared.error.BusinessRuleViolationException;
import io.github.kenercartagena.tps.shared.error.CommonErrorCode;
import io.github.kenercartagena.tps.shared.error.ConflictException;
import io.github.kenercartagena.tps.shared.error.ErrorCode;
import io.github.kenercartagena.tps.shared.error.ResourceNotFoundException;
import io.github.kenercartagena.tps.shared.error.TpsException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates every exception raised while handling a request into an
 * <a href="https://www.rfc-editor.org/rfc/rfc9457">RFC 9457 Problem Details</a>
 * response ({@code application/problem+json}).
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} covers the exceptions Spring MVC
 * raises itself (unknown endpoint, wrong HTTP method, unreadable body, ...). Every
 * response then passes through {@link #createResponseEntity}, which adds the TPS
 * extension properties:
 *
 * <ul>
 *   <li>{@code code} — stable, machine-readable error code ({@link ErrorCode}),</li>
 *   <li>{@code requestId} — correlates the response with the server logs,</li>
 *   <li>{@code timestamp} — when the error occurred (UTC),</li>
 *   <li>{@code errors} — field violations, only for validation failures,</li>
 *   <li>the extra properties of a {@link TpsException}, e.g. {@code transactionId}.</li>
 * </ul>
 *
 * <p>Unexpected exceptions return a generic {@code 500} without internal details;
 * the full stack trace is logged together with the request id.
 *
 * <p>Spring Security failures ({@code 401}/{@code 403}) also end here: the security
 * filter chain delegates them to this handler, so there is a single source of error
 * responses for the whole API (ADR 0004).
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final String CODE = "code";
    static final String REQUEST_ID = "requestId";
    static final String TIMESTAMP = "timestamp";
    static final String ERRORS = "errors";

    // ---- TPS business exceptions -------------------------------------------------

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<Object> handleResourceNotFound(ResourceNotFoundException ex, WebRequest request) {
        return handleTpsException(ex, HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<Object> handleConflict(ConflictException ex, WebRequest request) {
        return handleTpsException(ex, HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    ResponseEntity<Object> handleBusinessRuleViolation(BusinessRuleViolationException ex,
            WebRequest request) {
        return handleTpsException(ex, HttpStatus.UNPROCESSABLE_CONTENT, request);
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    ResponseEntity<Object> handleAuthenticationFailed(AuthenticationFailedException ex,
            WebRequest request) {
        return handleTpsException(ex, HttpStatus.UNAUTHORIZED, request);
    }

    /**
     * Two requests changed the same resource at the same time and this one lost the
     * optimistic lock (JPA {@code @Version}). Nothing was saved; the client can read the
     * resource again and retry.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Object> handleOptimisticLocking(OptimisticLockingFailureException ex,
            WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The resource was modified by another request. Read it again and retry.");
        problem.setProperty(CODE, CommonErrorCode.CONCURRENT_MODIFICATION.code());
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.CONFLICT, request);
    }

    /**
     * The request waited too long for a row another request had locked (the database
     * {@code lock_timeout}), or the database broke a deadlock by aborting it. Nothing was
     * saved: the whole database transaction was rolled back, so the client can retry.
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<Object> handlePessimisticLocking(PessimisticLockingFailureException ex,
            WebRequest request) {
        log.warn("Request gave up waiting for a database lock: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The resource is being changed by another request. Nothing was saved; retry in a moment.");
        problem.setProperty(CODE, CommonErrorCode.CONCURRENT_MODIFICATION.code());
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.CONFLICT, request);
    }

    // ---- Spring Security ---------------------------------------------------------

    /**
     * Missing, malformed, expired or tampered credentials. The exception message is not
     * returned: it can describe the token in detail. The {@code WWW-Authenticate}
     * header is set by the security entry point before delegating here.
     */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> handleAuthentication(AuthenticationException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Authentication is required. Provide a valid bearer token.");
        problem.setProperty(CODE, CommonErrorCode.AUTHENTICATION_REQUIRED.code());
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.UNAUTHORIZED,
                request);
    }

    /**
     * The caller is authenticated but not allowed to perform the operation. Raised by
     * {@code @PreAuthorize} inside a controller or by the security filter chain.
     * Without this handler the catch-all below would turn it into a {@code 500}.
     */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "You do not have permission to perform this operation.");
        problem.setProperty(CODE, CommonErrorCode.ACCESS_DENIED.code());
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.FORBIDDEN,
                request);
    }

    // ---- Anything else: a bug or an infrastructure failure -----------------------

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unexpected error while processing request", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Quote the requestId when reporting this problem.");
        problem.setProperty(CODE, CommonErrorCode.INTERNAL_ERROR.code());
        return handleExceptionInternal(ex, problem, new HttpHeaders(),
                HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    // ---- Spring MVC exceptions that need more than the default body --------------

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        List<FieldViolation> violations = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> violations.add(toViolation(error)));
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                violations.add(new FieldViolation(error.getObjectName(), error.getDefaultMessage())));
        return validationFailed(ex, ex.getBody(), violations, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        List<FieldViolation> violations = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors errors) {
                errors.getFieldErrors().forEach(error -> violations.add(toViolation(error)));
            } else {
                String parameter = result.getMethodParameter().getParameterName();
                for (MessageSourceResolvable error : result.getResolvableErrors()) {
                    violations.add(new FieldViolation(parameter, error.getDefaultMessage()));
                }
            }
        }
        return validationFailed(ex, ex.getBody(), violations, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
                "The request body is missing or is not valid JSON for this endpoint.");
        problem.setProperty(CODE, CommonErrorCode.MALFORMED_REQUEST.code());
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /** Unknown URL. Spring's default detail mentions "static resource", an internal detail. */
    @Override
    protected @Nullable ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
                "The requested endpoint does not exist.");
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    // ---- Common enrichment of every Problem Details response ---------------------

    @Override
    protected ResponseEntity<Object> createResponseEntity(@Nullable Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            enrich(problem, statusCode, request);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private ResponseEntity<Object> handleTpsException(TpsException ex, HttpStatus status,
            WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        ex.getProperties().forEach(problem::setProperty);
        problem.setProperty(CODE, ex.getErrorCode().code());
        return handleExceptionInternal(ex, problem, new HttpHeaders(), status, request);
    }

    private @Nullable ResponseEntity<Object> validationFailed(Exception ex, ProblemDetail problem,
            List<FieldViolation> violations, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        problem.setDetail("Request validation failed.");
        problem.setProperty(CODE, CommonErrorCode.VALIDATION_FAILED.code());
        problem.setProperty(ERRORS, violations);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    private static void enrich(ProblemDetail problem, HttpStatusCode status, WebRequest request) {
        Map<String, Object> properties = problem.getProperties();
        if (properties == null || !properties.containsKey(CODE)) {
            problem.setProperty(CODE, defaultCode(status).code());
        }
        problem.setProperty(REQUEST_ID, MDC.get(RequestIdFilter.MDC_KEY));
        problem.setProperty(TIMESTAMP, Instant.now().truncatedTo(ChronoUnit.MILLIS));
        if (problem.getInstance() == null
                && request instanceof NativeWebRequest nativeRequest
                && nativeRequest.getNativeRequest() instanceof HttpServletRequest servletRequest) {
            problem.setInstance(URI.create(servletRequest.getRequestURI()));
        }
    }

    private static ErrorCode defaultCode(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> CommonErrorCode.BAD_REQUEST;
            case 401 -> CommonErrorCode.AUTHENTICATION_REQUIRED;
            case 403 -> CommonErrorCode.ACCESS_DENIED;
            case 404 -> CommonErrorCode.NOT_FOUND;
            case 405 -> CommonErrorCode.METHOD_NOT_ALLOWED;
            case 406 -> CommonErrorCode.NOT_ACCEPTABLE;
            case 409 -> CommonErrorCode.CONFLICT;
            case 415 -> CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case 500 -> CommonErrorCode.INTERNAL_ERROR;
            default -> CommonErrorCode.REQUEST_FAILED;
        };
    }

    private static FieldViolation toViolation(FieldError error) {
        return new FieldViolation(error.getField(), error.getDefaultMessage());
    }
}
