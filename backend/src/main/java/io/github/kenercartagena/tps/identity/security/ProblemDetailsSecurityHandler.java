package io.github.kenercartagena.tps.identity.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Writes the {@code 401}/{@code 403} responses of the security filter chain in the same
 * RFC 9457 format as every other error.
 *
 * <p>Security filters run before Spring MVC, so the global exception handler would never
 * see these failures. This class hands them to Spring MVC's {@link HandlerExceptionResolver},
 * which calls the global handler: one place still produces every error body.
 *
 * <p>A {@code 401} also gets the {@code WWW-Authenticate} header of RFC 6750 (Bearer
 * tokens): {@code Bearer} when no token was sent, {@code Bearer error="invalid_token"} when
 * the token is malformed, tampered with or expired. A {@code 403} has no such header (it is
 * optional), so it looks the same whether the filter chain or {@code @PreAuthorize} denied
 * the request.
 *
 * <p>The header is written here instead of by Spring Security's Bearer entry point, which
 * adds an RFC 9728 {@code resource_metadata} link to an external authorization server:
 * TPS issues its own tokens. No {@code error_description} is sent either, because it can
 * describe the token in detail.
 */
@Component
class ProblemDetailsSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String NO_TOKEN = "Bearer";
    static final String INVALID_TOKEN = "Bearer error=\"invalid_token\"";

    private final HandlerExceptionResolver exceptionResolver;

    ProblemDetailsSecurityHandler(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) {
        // An OAuth2AuthenticationException means a token was presented but rejected.
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                exception instanceof OAuth2AuthenticationException ? INVALID_TOKEN : NO_TOKEN);
        exceptionResolver.resolveException(request, response, null, exception);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) {
        exceptionResolver.resolveException(request, response, null, exception);
    }
}
