package io.github.kenercartagena.tps.shared.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns a request id to every HTTP request so all log lines and the error response
 * of one request can be correlated.
 *
 * <ul>
 *   <li>If the client sends a valid {@value #HEADER} header, it is reused, so a request
 *       can be traced across systems; otherwise a random UUID is generated.</li>
 *   <li>Client values are only accepted if they match {@link #VALID_REQUEST_ID}, which
 *       prevents log injection (e.g. line breaks forging fake log entries).</li>
 *   <li>The id is stored in the logging MDC under {@value #MDC_KEY}, returned in the
 *       {@value #HEADER} response header and included in error responses.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Request-Id";
    static final String MDC_KEY = "requestId";

    /** Keeps the id stable when the same request is dispatched again (e.g. to /error). */
    private static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** Also run for error dispatches, so errors handled by the container keep the id. */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    private static String resolveRequestId(HttpServletRequest request) {
        if (request.getAttribute(ATTRIBUTE) instanceof String existing) {
            return existing;
        }
        String fromClient = request.getHeader(HEADER);
        if (fromClient != null && VALID_REQUEST_ID.matcher(fromClient).matches()) {
            return fromClient;
        }
        return UUID.randomUUID().toString();
    }
}
