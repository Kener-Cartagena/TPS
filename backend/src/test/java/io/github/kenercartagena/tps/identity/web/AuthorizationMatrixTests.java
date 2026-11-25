package io.github.kenercartagena.tps.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import io.github.kenercartagena.tps.identity.application.UserService;
import io.github.kenercartagena.tps.identity.application.UserView;
import io.github.kenercartagena.tps.identity.domain.Role;
import io.github.kenercartagena.tps.identity.domain.UserStatus;
import io.github.kenercartagena.tps.identity.security.AuthenticationService;
import io.github.kenercartagena.tps.identity.security.IssuedToken;
import io.github.kenercartagena.tps.identity.security.JwtAuthoritiesConverter;
import io.github.kenercartagena.tps.identity.security.WebSecurityTestConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Who may call what: every endpoint of the identity module, called by an anonymous
 * client and by each role, against the real security configuration.
 *
 * <p>The services are mocked, so this test checks <em>only</em> authorization. The
 * matrix below is the executable version of the table in {@code docs/security.md}: a
 * change to the permission model that is not reflected here fails the build.
 */
@WebMvcTest(controllers = {AuthController.class, UserController.class})
@Import(WebSecurityTestConfiguration.class)
class AuthorizationMatrixTests {

    private static final UUID CALLER_ID = UUID.fromString("0193a1b2-0000-7000-8000-00000000000a");
    private static final UUID OTHER_ID = UUID.fromString("0193a1b2-0000-7000-8000-00000000000b");

    private static final String CREATE_USER = """
            {"username": "new.user", "fullName": "New User",
             "password": "a-long-password", "role": "OPERATOR"}""";
    private static final String CHANGE_ROLE = """
            {"role": "AUDITOR"}""";
    private static final String CHANGE_STATUS = """
            {"status": "DISABLED"}""";
    private static final String CHANGE_PASSWORD = """
            {"currentPassword": "the-old-password", "newPassword": "the-new-password"}""";
    private static final String LOGIN = """
            {"username": "jane", "password": "a-long-password"}""";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthenticationService authenticationService;

    @BeforeEach
    void stubServices() {
        UserView user = new UserView(OTHER_ID, "new.user", "New User", Role.OPERATOR,
                UserStatus.ACTIVE, false, Instant.now(), Instant.now());
        given(userService.get(any())).willReturn(user);
        given(userService.create(any(), any())).willReturn(user);
        given(userService.list(anyInt(), anyInt())).willReturn(new PageImpl<>(List.of(user)));
        given(userService.changeRole(any(), any(), any())).willReturn(user);
        given(userService.changeStatus(any(), any(), any())).willReturn(user);
        given(authenticationService.login(any(), any()))
                .willReturn(new IssuedToken("token", Instant.now().plusSeconds(1800), 1800));
    }

    /**
     * One row per endpoint. Columns: anonymous, SYSTEM_ADMIN, OPERATOR, AUDITOR.
     */
    static Stream<Arguments> matrix() {
        return Stream.of(
                row(HttpMethod.POST, "/api/v1/auth/tokens", LOGIN, 200, 200, 200, 200),
                row(HttpMethod.GET, "/api/v1/users/me", null, 401, 200, 200, 200),
                row(HttpMethod.PUT, "/api/v1/users/me/password", CHANGE_PASSWORD, 401, 204, 204, 204),
                row(HttpMethod.POST, "/api/v1/users", CREATE_USER, 401, 201, 403, 403),
                row(HttpMethod.GET, "/api/v1/users", null, 401, 200, 403, 200),
                row(HttpMethod.GET, "/api/v1/users/" + OTHER_ID, null, 401, 200, 403, 200),
                row(HttpMethod.PUT, "/api/v1/users/" + OTHER_ID + "/role", CHANGE_ROLE, 401, 200, 403, 403),
                row(HttpMethod.PUT, "/api/v1/users/" + OTHER_ID + "/status", CHANGE_STATUS, 401, 200, 403, 403))
                .flatMap(rows -> rows);
    }

    @ParameterizedTest(name = "{0} {1} as {3} -> {4}")
    @MethodSource("matrix")
    void endpointEnforcesItsPermission(HttpMethod method, String path, @Nullable String body,
            String caller, int expectedStatus) {
        MockMvcRequestBuilder request = mvc.method(method).uri(path);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        if (!caller.equals("anonymous")) {
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

    @Test
    void missingTokenIsAnsweredWithBearerChallenge() {
        MvcTestResult result = mvc.get().uri("/api/v1/users/me").exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).hasHeader("WWW-Authenticate", "Bearer");
    }

    @Test
    void invalidTokenIsRejectedWithInvalidTokenChallenge() {
        MvcTestResult result = mvc.get().uri("/api/v1/users/me")
                .header("Authorization", "Bearer not-a-real-token")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).hasHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    void missingPermissionReturnsAccessDeniedProblem() {
        MvcTestResult result = mvc.get().uri("/api/v1/users")
                .with(jwt().jwt(token -> token.subject(CALLER_ID.toString()))
                        .authorities(JwtAuthoritiesConverter.authoritiesOf(Role.OPERATOR)))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
    }

    private static Stream<Arguments> row(HttpMethod method, String path, @Nullable String body,
            int anonymous, int admin, int operator, int auditor) {
        return Stream.of(
                Arguments.of(method, path, body, "anonymous", anonymous),
                Arguments.of(method, path, body, Role.SYSTEM_ADMIN.name(), admin),
                Arguments.of(method, path, body, Role.OPERATOR.name(), operator),
                Arguments.of(method, path, body, Role.AUDITOR.name(), auditor));
    }
}
