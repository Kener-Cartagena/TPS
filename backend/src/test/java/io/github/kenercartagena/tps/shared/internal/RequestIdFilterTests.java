package io.github.kenercartagena.tps.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTests {

    private final RequestIdFilter filter = new RequestIdFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final AtomicReference<String> requestIdSeenByChain = new AtomicReference<>();

    @Test
    void generatesRequestIdWhenClientSendsNone() throws Exception {
        runFilter();

        String requestId = response.getHeader(RequestIdFilter.HEADER);
        assertThat(requestId).isNotBlank();
        assertThat(UUID.fromString(requestId)).isNotNull();
        assertThat(requestIdSeenByChain).hasValue(requestId);
    }

    @Test
    void reusesValidRequestIdFromClient() throws Exception {
        request.addHeader(RequestIdFilter.HEADER, "client-trace-123");

        runFilter();

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("client-trace-123");
        assertThat(requestIdSeenByChain).hasValue("client-trace-123");
    }

    @Test
    void replacesRequestIdThatCouldInjectLogLines() throws Exception {
        request.addHeader(RequestIdFilter.HEADER, "abc\nFAKE LOG ENTRY");

        runFilter();

        String requestId = response.getHeader(RequestIdFilter.HEADER);
        assertThat(requestId).isNotEqualTo("abc\nFAKE LOG ENTRY");
        assertThat(UUID.fromString(requestId)).isNotNull();
    }

    @Test
    void replacesRequestIdThatIsTooLong() throws Exception {
        request.addHeader(RequestIdFilter.HEADER, "a".repeat(65));

        runFilter();

        assertThat(response.getHeader(RequestIdFilter.HEADER)).hasSize(36); // a UUID
    }

    @Test
    void clearsMdcAfterRequest() throws Exception {
        runFilter();

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    private void runFilter() throws Exception {
        filter.doFilter(request, response,
                (req, res) -> requestIdSeenByChain.set(MDC.get(RequestIdFilter.MDC_KEY)));
    }
}
