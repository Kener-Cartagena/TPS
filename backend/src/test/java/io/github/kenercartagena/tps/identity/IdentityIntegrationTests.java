package io.github.kenercartagena.tps.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kenercartagena.tps.TestcontainersConfiguration;
import io.github.kenercartagena.tps.identity.domain.Role;
import io.github.kenercartagena.tps.identity.security.SecurityProperties;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

/**
 * End-to-end security flows against a running server and a real PostgreSQL database:
 * login, tokens, user management, lockout and the administrator safety rules.
 *
 * <p>The database starts empty, so the bootstrap administrator from the configuration
 * ({@code application-dev.yml}) is the first user. Tests share the database: each one
 * creates users with unique names, and only {@link #lastActiveAdministratorCannotBeDisabled}
 * creates another administrator.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class IdentityIntegrationTests {

    private static final String LOGIN = "/api/v1/auth/tokens";
    private static final String USERS = "/api/v1/users";
    private static final String PASSWORD = "a-long-password-1";

    @Autowired
    private RestTestClient client;

    @Autowired
    private SecurityProperties securityProperties;

    private String adminToken;

    @BeforeEach
    void logInAsBootstrapAdministrator() {
        SecurityProperties.Admin admin = securityProperties.bootstrap().admin();
        adminToken = login(admin.username(), admin.password());
    }

    // ---- Login and tokens ----------------------------------------------------------

    @Test
    void loginReturnsBearerTokenWithExpiration() {
        SecurityProperties.Admin admin = securityProperties.bootstrap().admin();

        TokenBody token = loginAttempt(admin.username(), admin.password())
                .expectStatus().isOk()
                .expectBody(TokenBody.class).returnResult().getResponseBody();

        assertThat(token.accessToken()).isNotBlank();
        assertThat(token.tokenType()).isEqualTo("Bearer");
        assertThat(token.expiresIn()).isEqualTo(securityProperties.jwt().expiration().toSeconds());
        assertThat(token.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void loginIsCaseInsensitiveForTheUsername() {
        String username = uniqueUsername();
        createUser(username, Role.OPERATOR);

        loginAttempt(username.toUpperCase(), PASSWORD).expectStatus().isOk();
    }

    /** Wrong password and unknown user are indistinguishable. */
    @Test
    void failedLoginNeverRevealsWhetherTheUserExists() {
        expectInvalidCredentials(loginAttempt(securityProperties.bootstrap().admin().username(), "wrong-password-1"));
        expectInvalidCredentials(loginAttempt("nobody-" + UUID.randomUUID(), "wrong-password-1"));
    }

    /** bcrypt only reads 72 bytes; a longer login password must still be a plain 401. */
    @Test
    void passwordLongerThanBcryptAcceptsIsJustInvalidCredentials() {
        expectInvalidCredentials(loginAttempt(securityProperties.bootstrap().admin().username(), "x".repeat(128)));
    }

    @Test
    void callWithoutTokenIsRejected() {
        client.get().uri(USERS + "/me").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    void tamperedTokenIsRejected() {
        String tampered = adminToken.substring(0, adminToken.length() - 4) + "AAAA";

        client.get().uri(USERS + "/me").headers(h -> h.setBearerAuth(tampered)).exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer error=\"invalid_token\"")
                .expectBody().jsonPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    void authenticatedCallerGets404ForUnknownEndpoints() {
        client.get().uri("/api/v1/does-not-exist").headers(h -> h.setBearerAuth(adminToken)).exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("NOT_FOUND");
        client.get().uri("/actuator/env").headers(h -> h.setBearerAuth(adminToken)).exchange()
                .expectStatus().isNotFound();
    }

    // ---- User management -----------------------------------------------------------

    @Test
    void administratorCreatesUserWhoCanLogInWithItsRole() {
        String username = uniqueUsername();

        UserBody created = client.post().uri(USERS).headers(h -> h.setBearerAuth(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "fullName", "Olga Operator",
                        "password", PASSWORD, "role", "OPERATOR"))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueMatches("Location", ".*/api/v1/users/[0-9a-f-]{36}$")
                .expectBody(UserBody.class).returnResult().getResponseBody();

        assertThat(created.username()).isEqualTo(username);
        assertThat(created.role()).isEqualTo("OPERATOR");
        assertThat(created.status()).isEqualTo("ACTIVE");

        String operatorToken = login(username, PASSWORD);
        client.get().uri(USERS + "/me").headers(h -> h.setBearerAuth(operatorToken)).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(created.id().toString())
                .jsonPath("$.role").isEqualTo("OPERATOR")
                .jsonPath("$.passwordHash").doesNotExist();
    }

    @Test
    void operatorCannotManageUsers() {
        String username = uniqueUsername();
        createUser(username, Role.OPERATOR);
        String operatorToken = login(username, PASSWORD);

        client.get().uri(USERS).headers(h -> h.setBearerAuth(operatorToken)).exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("ACCESS_DENIED");
    }

    @Test
    void usernamesAreUniqueIgnoringCase() {
        String username = uniqueUsername();
        createUser(username, Role.AUDITOR);

        createUserRequest(username.toUpperCase(), Role.AUDITOR)
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("USERNAME_ALREADY_EXISTS");
    }

    @Test
    void weakPasswordIsRejected() {
        client.post().uri(USERS).headers(h -> h.setBearerAuth(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", uniqueUsername(), "fullName", "Weak",
                        "password", "short", "role", "OPERATOR"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.errors[0].field").isEqualTo("password");
    }

    @Test
    void usersAreListedInPages() {
        createUser(uniqueUsername(), Role.AUDITOR);
        createUser(uniqueUsername(), Role.AUDITOR);

        client.get().uri(USERS + "?page=0&size=1").headers(h -> h.setBearerAuth(adminToken)).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.page.size").isEqualTo(1)
                .jsonPath("$.page.number").isEqualTo(0)
                .jsonPath("$.page.totalElements").value(Integer.class, total -> assertThat(total).isGreaterThanOrEqualTo(3))
                .jsonPath("$.content[0].passwordHash").doesNotExist();
    }

    @Test
    void pageSizeIsLimited() {
        client.get().uri(USERS + "?size=101").headers(h -> h.setBearerAuth(adminToken)).exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("size");
    }

    @Test
    void disabledUserCannotLogInUntilActivatedAgain() {
        String username = uniqueUsername();
        UUID userId = createUser(username, Role.OPERATOR);

        changeStatus(adminToken, userId, "DISABLED").expectStatus().isOk();
        expectInvalidCredentials(loginAttempt(username, PASSWORD));

        changeStatus(adminToken, userId, "ACTIVE").expectStatus().isOk();
        loginAttempt(username, PASSWORD).expectStatus().isOk();
    }

    @Test
    void roleChangeTakesEffectAtTheNextLogin() {
        String username = uniqueUsername();
        UUID userId = createUser(username, Role.OPERATOR);

        client.put().uri(USERS + "/" + userId + "/role").headers(h -> h.setBearerAuth(adminToken))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("role", "AUDITOR"))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.role").isEqualTo("AUDITOR");

        String auditorToken = login(username, PASSWORD);
        client.get().uri(USERS).headers(h -> h.setBearerAuth(auditorToken)).exchange()
                .expectStatus().isOk();
    }

    // ---- Brute-force protection ----------------------------------------------------

    @Test
    void accountLocksAfterTooManyFailedLoginsUntilAnAdministratorUnlocksIt() {
        String username = uniqueUsername();
        UUID userId = createUser(username, Role.OPERATOR);
        int maxAttempts = securityProperties.login().maxAttempts();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            expectInvalidCredentials(loginAttempt(username, "wrong-password-" + attempt));
        }

        // The right password no longer works, and the response does not say why.
        expectInvalidCredentials(loginAttempt(username, PASSWORD));
        client.get().uri(USERS + "/" + userId).headers(h -> h.setBearerAuth(adminToken)).exchange()
                .expectBody().jsonPath("$.locked").isEqualTo(true);

        changeStatus(adminToken, userId, "ACTIVE").expectStatus().isOk();
        loginAttempt(username, PASSWORD).expectStatus().isOk();
    }

    @Test
    void successfulLoginResetsTheFailedAttempts() {
        String username = uniqueUsername();
        createUser(username, Role.OPERATOR);
        int maxAttempts = securityProperties.login().maxAttempts();

        for (int attempt = 1; attempt < maxAttempts; attempt++) {
            expectInvalidCredentials(loginAttempt(username, "wrong-password-" + attempt));
        }
        loginAttempt(username, PASSWORD).expectStatus().isOk();
        for (int attempt = 1; attempt < maxAttempts; attempt++) {
            expectInvalidCredentials(loginAttempt(username, "wrong-password-" + attempt));
        }

        loginAttempt(username, PASSWORD).expectStatus().isOk();
    }

    // ---- Own password --------------------------------------------------------------

    @Test
    void userChangesOwnPasswordWithTheCurrentOne() {
        String username = uniqueUsername();
        createUser(username, Role.AUDITOR);
        String token = login(username, PASSWORD);

        changePassword(token, "not-the-password", "a-brand-new-password")
                .expectStatus().isEqualTo(422)
                .expectBody().jsonPath("$.code").isEqualTo("CURRENT_PASSWORD_INCORRECT");

        changePassword(token, PASSWORD, "a-brand-new-password").expectStatus().isNoContent();
        expectInvalidCredentials(loginAttempt(username, PASSWORD));
        loginAttempt(username, "a-brand-new-password").expectStatus().isOk();
    }

    // ---- Administrator safety rules ------------------------------------------------

    @Test
    void administratorCannotChangeOwnRoleOrStatus() {
        UUID adminId = myId(adminToken);

        changeStatus(adminToken, adminId, "DISABLED")
                .expectStatus().isEqualTo(422)
                .expectBody().jsonPath("$.code").isEqualTo("SELF_MODIFICATION_NOT_ALLOWED");
    }

    /**
     * A disabled administrator keeps a valid token until it expires (ADR 0005). Even then
     * they cannot disable the last active administrator.
     */
    @Test
    void lastActiveAdministratorCannotBeDisabled() {
        String secondAdmin = uniqueUsername();
        UUID secondAdminId = createUser(secondAdmin, Role.SYSTEM_ADMIN);
        String secondAdminToken = login(secondAdmin, PASSWORD);

        changeStatus(adminToken, secondAdminId, "DISABLED").expectStatus().isOk();

        changeStatus(secondAdminToken, myId(adminToken), "DISABLED")
                .expectStatus().isEqualTo(422)
                .expectBody().jsonPath("$.code").isEqualTo("LAST_ACTIVE_ADMIN");
    }

    // ---- Helpers -------------------------------------------------------------------

    private ResponseSpec loginAttempt(String username, String password) {
        return client.post().uri(LOGIN).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", password))
                .exchange();
    }

    private String login(String username, String password) {
        return loginAttempt(username, password).expectStatus().isOk()
                .expectBody(TokenBody.class).returnResult().getResponseBody().accessToken();
    }

    private static void expectInvalidCredentials(ResponseSpec response) {
        response.expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_CREDENTIALS")
                .jsonPath("$.detail").isEqualTo("Invalid username or password.");
    }

    private ResponseSpec createUserRequest(String username, Role role) {
        return client.post().uri(USERS).headers(h -> h.setBearerAuth(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "fullName", "Test User",
                        "password", PASSWORD, "role", role.name()))
                .exchange();
    }

    private UUID createUser(String username, Role role) {
        return createUserRequest(username, role).expectStatus().isCreated()
                .expectBody(UserBody.class).returnResult().getResponseBody().id();
    }

    private ResponseSpec changeStatus(String token, UUID userId, String status) {
        return client.put().uri(USERS + "/" + userId + "/status").headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("status", status))
                .exchange();
    }

    private ResponseSpec changePassword(String token, String current, String next) {
        return client.put().uri(USERS + "/me/password").headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("currentPassword", current, "newPassword", next))
                .exchange();
    }

    private UUID myId(String token) {
        return client.get().uri(USERS + "/me").headers(h -> h.setBearerAuth(token)).exchange()
                .expectStatus().isOk()
                .expectBody(UserBody.class).returnResult().getResponseBody().id();
    }

    private static String uniqueUsername() {
        return "user-" + UUID.randomUUID().toString().substring(0, 8);
    }

    record TokenBody(String accessToken, String tokenType, long expiresIn, Instant expiresAt) {
    }

    record UserBody(UUID id, String username, String fullName, String role, String status,
            boolean locked, Instant createdAt, Instant updatedAt) {
    }
}
