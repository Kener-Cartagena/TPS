package io.github.kenercartagena.tps.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import io.github.kenercartagena.tps.identity.domain.Role;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Building blocks for the authorization matrix tests of each module: one row per
 * endpoint, one expected status per caller (anonymous and each role). Use with
 * {@code @WebMvcTest} and {@link WebSecurityTestConfiguration}, so the real security
 * configuration decides.
 */
public final class AuthorizationMatrix {

    public static final String ANONYMOUS = "anonymous";

    /** The caller's user id (the {@code sub} of its token). */
    public static final UUID CALLER_ID = UUID.fromString("0193a1b2-0000-7000-8000-00000000000a");

    private AuthorizationMatrix() {
    }

    /** Arguments: method, path, body, caller, expected status. */
    public static Stream<Arguments> row(HttpMethod method, String path, @Nullable String body,
            int anonymous, int admin, int operator, int auditor) {
        return Stream.of(
                Arguments.of(method, path, body, ANONYMOUS, anonymous),
                Arguments.of(method, path, body, Role.SYSTEM_ADMIN.name(), admin),
                Arguments.of(method, path, body, Role.OPERATOR.name(), operator),
                Arguments.of(method, path, body, Role.AUDITOR.name(), auditor));
    }

    /** Calls the endpoint as the caller and checks the status (and error code if denied). */
    public static void assertCall(MockMvcTester mvc, HttpMethod method, String path,
            @Nullable String body, String caller, int expectedStatus) {
        MockMvcRequestBuilder request = mvc.method(method).uri(path);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        if (!caller.equals(ANONYMOUS)) {
            Role role = Role.valueOf(caller);
            request.with(jwt()
                    .jwt(token -> token.subject(CALLER_ID.toString()).claim("role", role.name()))
                    .authorities(JwtAuthoritiesConverter.authoritiesOf(role)));
        }

        MvcTestResult result = request.exchange();

        assertThat(result).hasStatus(expectedStatus);
        if (expectedStatus == 401 || expectedStatus == 403) {
            assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(result).bodyJson().extractingPath("$.code")
                    .isEqualTo(expectedStatus == 401 ? "AUTHENTICATION_REQUIRED" : "ACCESS_DENIED");
        }
    }
}
